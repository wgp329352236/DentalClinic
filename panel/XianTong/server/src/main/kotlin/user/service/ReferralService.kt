package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.ReferralRewardOutcome
import com.dentalclinic.common.ReferralRewardService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Referral service for user growth program.
 */
class ReferralService(
    private val pointsService: PointsService,
    private val referralRewardService: ReferralRewardService
) : BaseService() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 获取或生成用户自己的推荐码。
     *
     * 推荐码只存在于「自引用占位行」（referrer_id = referee_id = 本人）上。
     * 真实推荐关系行（referrer_id = 推荐人）只表达「谁推荐了谁」，不承载本人的分享码——
     * 两类行可以并存（由部分唯一索引 ref_rel_uk_referee_real 保证一个用户最多只有一条真实关系）。
     *
     * 因此这里必须精确定位自引用行；用户被推荐后其 referrer_id 维度下会多出真实关系行，
     * 只按 referrerId 取 limit(1) 可能拿到关系行而错把推荐人的码当成自己的码。
     */
    suspend fun getReferralCode(userId: Long): String = dbQuery {
        val existing = ReferralRelations.selectAll()
            .where {
                (ReferralRelations.referrerId eq userId) and
                (ReferralRelations.refereeId eq userId)
            }
            .limit(1)
            .toList()
            .singleOrNull()

        if (existing != null) {
            existing[ReferralRelations.referralCode]
        } else {
            val code = "REF${userId}${System.currentTimeMillis().toString().takeLast(6)}"
            ReferralRelations.insert {
                it[ReferralRelations.referrerId] = userId
                it[ReferralRelations.refereeId] = userId
                it[ReferralRelations.referralCode] = code
                it[ReferralRelations.registeredAt] = LocalDateTime.now()
                it[ReferralRelations.rewardStatus] = "active"
            }
            code
        }
    }

    /**
     * Get user's referral records (people they referred).
     */
    suspend fun getReferralRecords(userId: Long, page: Int, pageSize: Int): ReferralRecordsResponse = dbQuery {
        val allRows = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId eq userId }
            .orderBy(ReferralRelations.registeredAt, SortOrder.DESC)
            .toList()

        // Filter out self-references (placeholder records)
        val filtered = allRows.filter { row ->
            row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
        }
        val total = filtered.size.toLong()
        
        // Calculate monthly count
        val monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay()
        val monthlyCount = filtered.count { row ->
            row[ReferralRelations.registeredAt] >= monthStart
        }
        
        val paginatedRows = filtered.drop((page - 1) * pageSize).take(pageSize)

        // Batch load referee info to avoid N+1
        val refereeIds = paginatedRows.map { it[ReferralRelations.refereeId] }.distinct()
        val usersMap = if (refereeIds.isNotEmpty()) {
            Users.selectAll()
                .where { Users.id inList(refereeIds) }
                .toList()
                .associateBy { it[Users.id] }
        } else emptyMap()

        val records = paginatedRows.map { row ->
            val referee = usersMap[row[ReferralRelations.refereeId]]
            val rawName = referee?.get(Users.nickname) ?: "用户${row[ReferralRelations.refereeId]}"
            // Desensitize name: show first char + ** for privacy
            val desensitizedName = if (rawName.length > 1) "${rawName[0]}**" else rawName
            
            // Calculate rewards based on status
            val rewardPoints = when (row[ReferralRelations.rewardStatus]) {
                "full" -> 120 // 20 (registration) + 100 (first consumption)
                "partial" -> 20 // Only registration reward
                else -> 0
            }
            val rewardBonus = if (row[ReferralRelations.rewardStatus] == "full") 50.0 else 0.0
            
            ReferralRecordDto(
                id = row[ReferralRelations.id],
                refereeName = desensitizedName,
                registeredAt = row[ReferralRelations.registeredAt].format(dateTimeFormatter),
                firstConsumedAt = row[ReferralRelations.firstConsumedAt]?.format(dateTimeFormatter),
                rewardStatus = row[ReferralRelations.rewardStatus],
                rewardPoints = rewardPoints,
                rewardBonus = rewardBonus
            )
        }

        ReferralRecordsResponse(
            list = records,
            total = total,
            monthlyCount = monthlyCount,
            page = page,
            pageSize = pageSize
        )
    }

    /**
     * 新用户凭推荐码绑定推荐关系。
     *
     * 数据模型（与历史版本不同）：自引用占位行（本人推荐码）与真实推荐关系行并存。
     * - 被推荐人可能先打开过自己的推荐页（已有占位行），也可能完全没访问过（无行），
     *   两种情况下绑定关系都是**新增一条真实关系行**，不再 UPDATE 占位行——
     *   否则会抹掉被推荐人自己的推荐码，导致其后续分享链接失效。
     * - 一个用户最多一条真实关系行，由部分唯一索引
     *   `ref_rel_uk_referee_real ON (referee_id) WHERE referrer_id <> referee_id` 兜底。
     *
     * 绑定成功同时回写 user.source_type='referral' / referred_by（仅当来源仍是默认 self，
     * 不覆盖门店录入 store_entry 等更高可信来源）。
     * 全程单事务。
     */
    suspend fun registerWithReferral(refereeId: Long, referralCode: String): ReferralResult = dbQuery {
        // 推荐码以自引用占位行为准；兼容老数据（码只挂在关系行上）再回退一次全表查
        val codeOwner = ReferralRelations.selectAll()
            .where {
                (ReferralRelations.referralCode eq referralCode) and
                (ReferralRelations.referrerId eq ReferralRelations.refereeId)
            }
            .toList()
            .firstOrNull()
            ?: ReferralRelations.selectAll()
                .where { ReferralRelations.referralCode eq referralCode }
                .toList()
                .firstOrNull()
            ?: return@dbQuery ReferralResult.Error("无效的推荐码")

        val referrerId = codeOwner[ReferralRelations.referrerId]

        if (referrerId == refereeId) {
            return@dbQuery ReferralResult.Error("不能使用自己的推荐码")
        }

        // Check referrer status
        val referrerStatus = Users.selectAll()
            .where { Users.id eq referrerId }
            .toList()
            .singleOrNull()
            ?.get(Users.status)

        if (referrerStatus == 0) {
            return@dbQuery ReferralResult.Error("推荐人账户已被禁用")
        }

        // 被推荐人是否已存在真实推荐关系（占位行不算）
        val alreadyReferred = ReferralRelations.selectAll()
            .where { ReferralRelations.refereeId eq refereeId }
            .toList()
            .any { row ->
                row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
            }

        if (alreadyReferred) {
            return@dbQuery ReferralResult.Error("该用户已被推荐")
        }

        // 新增真实关系行（与本人占位行并存）
        ReferralRelations.insert {
            it[ReferralRelations.referrerId] = referrerId
            it[ReferralRelations.refereeId] = refereeId
            it[ReferralRelations.referralCode] = referralCode
            it[ReferralRelations.registeredAt] = LocalDateTime.now()
            it[ReferralRelations.rewardStatus] = "pending"
        }

        // 回写用户来源：只在默认 self 时覆盖，不与门店录入(store_entry)等高可信来源抢可信度。
        // 条件更新 flipped=0 时静默跳过：推荐关系行已落库，绑定本身仍然成功。
        Users.update({
            (Users.id eq refereeId) and (Users.sourceType eq "self")
        }) {
            it[Users.referredBy] = referrerId
            it[Users.sourceType] = "referral"
        }

        // P1-8: 注册奖励延迟到首消后发放，此处不再立即发放
        ReferralResult.Success
    }

    /**
     * Award points for successful referral registration.
     */
    private suspend fun awardRegistrationReward(referrerId: Long, refereeId: Long) {
        val rule = PointsRules.selectAll()
            .where {
                (PointsRules.actionType eq "referral") and
                (PointsRules.status eq 1)
            }
            .toList()
            .singleOrNull()

        if (rule != null) {
            val pointsEarned = rule[PointsRules.pointsPerUnit]
            val validityMonths = rule[PointsRules.validityMonths]

            pointsService.addPoints(
                userId = referrerId,
                amount = pointsEarned,
                sourceType = "referral_registration",
                description = "推荐注册奖励",
                validityMonths = validityMonths
            )

            // Update referral relation status
            ReferralRelations.update({
                (ReferralRelations.referrerId eq referrerId) and
                (ReferralRelations.refereeId eq refereeId)
            }) {
                it[ReferralRelations.rewardStatus] = "partial"
            }
        }
    }

    /**
     * 发放被推荐人的首消奖励。
     *
     * 实际发放逻辑已下沉到 [ReferralRewardService]（common 包），管理端「录入消费」走的是
     * 同一套首消/持续/阶梯奖励与月度上限规则，避免两份实现漂移。
     *
     * 生产环境应由消费结算链路自动触发，本方法仅保留给测试端点使用。
     *
     * @param payAmount 模拟的实付金额；已首消过时作为持续奖励的计算基数
     */
    suspend fun awardFirstConsumptionReward(
        refereeId: Long,
        payAmount: BigDecimal = BigDecimal.ZERO
    ): ReferralResult = dbQuery {
        when (val outcome = referralRewardService.settleConsumptionReward(refereeId, payAmount)) {
            ReferralRewardOutcome.NoRelation -> ReferralResult.Error("该用户没有推荐关系")
            is ReferralRewardOutcome.FirstConsumption -> ReferralResult.Success
            is ReferralRewardOutcome.Ongoing -> ReferralResult.Success
            is ReferralRewardOutcome.NoReward ->
                ReferralResult.Error("首消奖励已发放过；本次持续奖励未触发（${outcome.reason}）")
        }
    }
}

// DTOs

data class ReferralRecordDto(
    val id: Long,
    val refereeName: String,
    val registeredAt: String,
    val firstConsumedAt: String?,
    val rewardStatus: String,
    val rewardPoints: Int = 0,
    val rewardBonus: Double = 0.0
)

data class ReferralRecordsResponse(
    val list: List<ReferralRecordDto>,
    val total: Long,
    val monthlyCount: Int,
    val page: Int,
    val pageSize: Int
)

sealed class ReferralResult {
    object Success : ReferralResult()
    data class Error(val message: String) : ReferralResult()
}
