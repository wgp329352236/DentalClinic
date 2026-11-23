package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.UserMemberLevels
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.security.PhoneCipherService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * C 端个人资料服务。
 *
 * 原先这些查询裸写在 UserProfileRoutes 里并直接调用 suspendTransaction，
 * 与项目「Service 继承 BaseService、统一走 dbQuery」的约定不符，故下沉至此。
 */
class UserProfileService : BaseService() {

    private val logger = LoggerFactory.getLogger("UserProfileService")

    /**
     * 读取当前用户的资料，含钱包余额与会员等级信息。
     *
     * 会员等级部分服务需求 3.7.1「展示会员等级卡片，包含当前等级、距升级还需消费金额、
     * 等级权益说明」。此前 C 端拿不到这些字段，小程序只能硬编码一个 Lv1 普通会员，
     * 导致等级卡片与升级进度条长期展示假数据。
     *
     * @return 用户不存在时返回 null
     */
    suspend fun getProfile(userId: Long): UserProfileDto? = dbQuery {
        val userRow = Users.selectAll()
            .where { Users.id eq userId }
            .toList()
            .singleOrNull()
            ?: return@dbQuery null

        val walletRow = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList()
            .singleOrNull()

        val levelRow = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq userId }
            .toList()
            .singleOrNull()

        // 等级表只有 4 行且极少变动，一次取全后在内存里算当前级与下一级，避免二次查询
        val levels = MemberLevels.selectAll()
            .where { MemberLevels.status eq 1 }
            .orderBy(MemberLevels.levelCode, SortOrder.ASC)
            .toList()

        val levelId = levelRow?.get(UserMemberLevels.levelId)
        // 关联记录缺失时兜底到最低等级：新注册用户可能还没写入 user_member_level，
        // 此时按 Lv1 展示比返回 null 让前端整个卡片消失更符合需求（Lv1 是「注册即为」）
        val currentLevel = levels.firstOrNull { it[MemberLevels.id] == levelId } ?: levels.firstOrNull()
        val accumulated = levelRow?.get(UserMemberLevels.accumulatedConsumption) ?: BigDecimal.ZERO

        // 下一级 = levelCode 严格大于当前级的第一条（levels 已按 levelCode 升序）
        val nextLevel = currentLevel?.let { cur ->
            val curCode = cur[MemberLevels.levelCode]
            levels.firstOrNull { it[MemberLevels.levelCode] > curCode }
        }

        UserProfileDto(
            userId = userRow[Users.id],
            openid = userRow[Users.openid],
            phone = PhoneCipherService.readPhone(userRow[Users.phoneCipher], userRow[Users.phone]),
            nickname = userRow[Users.nickname],
            realName = userRow[Users.realName],
            gender = userRow[Users.gender],
            age = userRow[Users.age],
            avatar = userRow[Users.avatar],
            balance = walletRow?.get(UserWallets.balance)?.toDouble() ?: 0.0,
            bonusBalance = walletRow?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0,
            levelCode = currentLevel?.get(MemberLevels.levelCode) ?: 1,
            levelName = currentLevel?.get(MemberLevels.levelName) ?: "普通会员",
            discountRate = currentLevel?.get(MemberLevels.discountRate)?.toDouble() ?: 1.0,
            benefits = parseBenefits(currentLevel?.get(MemberLevels.benefits)),
            accumulatedConsumption = accumulated.toDouble(),
            // 已是最高等级时为 null：前端据此隐藏升级进度条，而不是显示「还需消费 0 元」
            nextLevelCost = nextLevel?.let {
                (it[MemberLevels.minConsumption] - accumulated)
                    .coerceAtLeast(BigDecimal.ZERO)
                    .toDouble()
            }
        )
    }

    /**
     * 更新当前用户的资料，仅覆盖请求中非 null 的字段。
     *
     * 手机号只做格式校验，不做验证码校验：短信通道与真实微信 code2Session 均未接入，
     * 重复号码由 cuser_uk_phone_hash 盲索引唯一约束兜底，冲突经 translateApiError 翻译成 409 友好文案。
     */
    suspend fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileUpdateResult {
        if (!request.phone.isNullOrEmpty() && !PhoneCipherService.isValidMainlandMobile(request.phone)) {
            return ProfileUpdateResult.Error("手机号格式不正确")
        }
        if (request.gender != null && request.gender !in listOf(1, 2)) {
            return ProfileUpdateResult.Error("性别值无效，应为1(男)或2(女)")
        }
        if (request.age != null && request.age !in 0..150) {
            return ProfileUpdateResult.Error("年龄值无效")
        }
        // 空姓名/昵称会让「我的」页顶部落成空白，与手机号一样按格式问题拒绝而不是存空串
        if (request.realName != null && request.realName.isBlank()) {
            return ProfileUpdateResult.Error("姓名不能为空")
        }
        if (request.nickname != null && request.nickname.isBlank()) {
            return ProfileUpdateResult.Error("昵称不能为空")
        }

        // 唯一约束冲突（如手机号已被占用）不在此处捕获：
        // 交由路由层的 translateApiError 统一翻译，保持 C 端错误映射只有一处
        return dbQuery {
            val exists = Users.selectAll()
                .where { Users.id eq userId }
                .toList()
                .isNotEmpty()
            if (!exists) return@dbQuery ProfileUpdateResult.NotFound

            val updated = Users.update({ Users.id eq userId }) {
                if (request.realName != null) it[Users.realName] = request.realName
                if (request.phone != null) {
                    if (request.phone.isBlank()) {
                        it[Users.phone] = null
                        it[Users.phoneCipher] = null
                        it[Users.phoneHash] = null
                    } else {
                        val encrypted = PhoneCipherService.encrypt(request.phone)
                        it[Users.phone] = null
                        it[Users.phoneCipher] = encrypted.ciphertext
                        it[Users.phoneHash] = encrypted.blindIndex
                    }
                }
                if (request.gender != null) it[Users.gender] = request.gender
                if (request.age != null) it[Users.age] = request.age
                if (request.avatar != null) it[Users.avatar] = request.avatar
                if (request.nickname != null) it[Users.nickname] = request.nickname
            }
            if (updated > 0) ProfileUpdateResult.Success else ProfileUpdateResult.NotFound
        }
    }

    /**
     * 导出当前用户全部个人数据（需求 5.2 数据可携带权）。
     *
     * 汇总用户主表、钱包、会员等级、积分等个人相关信息，供用户自行下载 JSON 存档。
     * 不下发 openid——它是平台内部标识，对用户本人无意义且属可复用凭证，不应外泄。
     *
     * @return 用户不存在时返回 null
     */
    suspend fun exportProfile(userId: Long): UserDataExportDto? = dbQuery {
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .toList()
            .singleOrNull()
            ?: return@dbQuery null

        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList()
            .singleOrNull()

        val levelRow = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq userId }
            .toList()
            .singleOrNull()

        val levelName = levelRow?.let { lr ->
            MemberLevels.selectAll()
                .where { MemberLevels.id eq lr[UserMemberLevels.levelId] }
                .toList()
                .singleOrNull()
                ?.get(MemberLevels.levelName)
        }

        val points = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()

        UserDataExportDto(
            userId = user[Users.id],
            nickname = user[Users.nickname],
            realName = user[Users.realName],
            phone = PhoneCipherService.readPhone(user[Users.phoneCipher], user[Users.phone]),
            gender = user[Users.gender],
            age = user[Users.age],
            avatar = user[Users.avatar],
            sourceType = user[Users.sourceType],
            registeredAt = user[Users.createdAt].toString(),
            balance = wallet?.get(UserWallets.balance)?.toDouble() ?: 0.0,
            bonusBalance = wallet?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0,
            levelName = levelName,
            accumulatedConsumption = levelRow?.get(UserMemberLevels.accumulatedConsumption)?.toDouble() ?: 0.0,
            totalPoints = points?.get(UserPointsSummary.totalBalance) ?: 0,
            exportedAt = LocalDateTime.now().toString()
        )
    }

    /**
     * 注销当前账户（需求 5.2 用户数据删除 + P0-4 合规：隐名化）。
     *
     * 采用隐名化删除：将手机号、姓名等 PII 替换为 "DELETED_{userId}" 占位符，
     * 保留业务记录以满足财务/审计追溯，同时通过
     * [com.dentalclinic.user.service.UserAuthService] 登录拦截使该账户无法再次登录。
     *
     * @return true 表示注销成功；false 表示用户不存在
     */
    suspend fun deleteAccount(userId: Long): Boolean = dbQuery {
        val anonymizedName = "已注销用户"
        Users.update({ Users.id eq userId }) {
            it[Users.status] = 0
            // 注销后不保留可恢复手机号；盲索引用不可碰撞占位值，密文和明文均清空。
            it[Users.phone] = null
            it[Users.phoneCipher] = null
            it[Users.phoneHash] = PhoneCipherService.deletedBlindIndex(userId)
            it[Users.realName] = anonymizedName
            it[Users.nickname] = anonymizedName
            it[Users.avatar] = null
            it[Users.updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * 解析 member_level.benefits。
     *
     * 库里存的是 `{"benefits":["预约优先","生日祝福"]}` 形式的 JSON 文本，列类型是 text
     * 而非 exposed-json，因此需要手工反序列化。解析失败按空权益降级：
     * 一条脏数据不该让整个 /profile/me 接口 500。
     */
    private fun parseBenefits(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString<BenefitsPayload>(raw).benefits
        } catch (e: Exception) {
            // 只记 message：异常文本可能带上原始 JSON 内容，堆栈对定位格式问题没有帮助
            logger.warn("[UserProfile]: 会员权益 JSON 解析失败，按空权益展示: ${e.message}")
            emptyList()
        }
    }

    /** 供 [parseBenefits] 使用的等级表结构，与 DataSeedService 写入的 JSON 一致 */
    @Serializable
    private data class BenefitsPayload(val benefits: List<String> = emptyList())
}

@Serializable
data class UpdateProfileRequest(
    val realName: String? = null,
    val phone: String? = null,
    val gender: Int? = null,
    val age: Int? = null,
    val avatar: String? = null,
    val nickname: String? = null
)

@Serializable
data class UserProfileDto(
    val userId: Long,
    val openid: String,
    val phone: String?,
    val nickname: String?,
    val realName: String?,
    val gender: Int?,
    val age: Int?,
    val avatar: String?,
    val balance: Double,
    val bonusBalance: Double,
    // ─── 会员等级（需求 3.7.1 等级卡片） ───
    val levelCode: Int,
    val levelName: String,
    val discountRate: Double,
    val benefits: List<String>,
    val accumulatedConsumption: Double,
    /** 距下一级还需消费的金额；已是最高等级时为 null */
    val nextLevelCost: Double?
)

sealed class ProfileUpdateResult {
    data object Success : ProfileUpdateResult()
    data object NotFound : ProfileUpdateResult()
    data class Error(val message: String) : ProfileUpdateResult()
}

/** 用户个人数据导出（需求 5.2 数据可携带权）。 */
@Serializable
data class UserDataExportDto(
    val userId: Long,
    val nickname: String?,
    val realName: String?,
    val phone: String?,
    val gender: Int?,
    val age: Int?,
    val avatar: String?,
    val sourceType: String,
    val registeredAt: String,
    val balance: Double,
    val bonusBalance: Double,
    val levelName: String?,
    val accumulatedConsumption: Double,
    val totalPoints: Int,
    val exportedAt: String
)
