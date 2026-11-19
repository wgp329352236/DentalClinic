package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.ReferralRelations
import com.dentalclinic.admin.entity.ReferralRewards
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 后台推荐活动管理服务。
 */
class AdminReferralService : BaseService() {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 分页查询推荐关系列表（排除用户自身的占位记录）。
     */
    suspend fun getReferralRelations(
        referrerId: Long?,
        rewardStatus: String?,
        page: Int,
        pageSize: Int
    ): ReferralRelationsResponse = dbQuery {
        var query = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId neq ReferralRelations.refereeId }

        if (referrerId != null) {
            query = query.andWhere { ReferralRelations.referrerId eq referrerId }
        }
        if (!rewardStatus.isNullOrBlank()) {
            query = query.andWhere { ReferralRelations.rewardStatus eq rewardStatus }
        }

        val total = query.count()
        val rows = query
            .orderBy(ReferralRelations.registeredAt, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        // 批量加载推荐人/被推荐人信息，避免 N+1 查询
        val userIds = rows.flatMap {
            listOf(it[ReferralRelations.referrerId], it[ReferralRelations.refereeId])
        }.distinct()
        val usersMap = loadUsersMap(userIds)

        val list = rows.map { row ->
            val referrerIdVal = row[ReferralRelations.referrerId]
            val refereeIdVal = row[ReferralRelations.refereeId]
            ReferralRelationAdminDto(
                id = row[ReferralRelations.id],
                referrerId = referrerIdVal,
                referrerName = usersMap[referrerIdVal]?.get(Users.nickname) ?: "用户$referrerIdVal",
                refereeId = refereeIdVal,
                refereeName = usersMap[refereeIdVal]?.get(Users.nickname) ?: "用户$refereeIdVal",
                referralCode = row[ReferralRelations.referralCode],
                registeredAt = row[ReferralRelations.registeredAt].format(formatter),
                firstConsumedAt = row[ReferralRelations.firstConsumedAt]?.format(formatter),
                rewardStatus = row[ReferralRelations.rewardStatus]
            )
        }

        ReferralRelationsResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    /**
     * 分页查询推荐奖励发放记录。
     */
    suspend fun getReferralRewards(
        referrerId: Long?,
        stage: String?,
        status: String?,
        page: Int,
        pageSize: Int
    ): ReferralRewardsResponse = dbQuery {
        var query = ReferralRewards.selectAll()

        if (referrerId != null) {
            query = query.andWhere { ReferralRewards.referrerId eq referrerId }
        }
        if (!stage.isNullOrBlank()) {
            query = query.andWhere { ReferralRewards.stage eq stage }
        }
        if (!status.isNullOrBlank()) {
            query = query.andWhere { ReferralRewards.status eq status }
        }

        val total = query.count()
        val rows = query
            .orderBy(ReferralRewards.createdAt, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        val userIds = rows.flatMap {
            listOf(it[ReferralRewards.referrerId], it[ReferralRewards.refereeId])
        }.distinct().filter { it > 0 }
        val usersMap = loadUsersMap(userIds)

        val list = rows.map { row ->
            val referrerIdVal = row[ReferralRewards.referrerId]
            val refereeIdVal = row[ReferralRewards.refereeId]
            ReferralRewardAdminDto(
                id = row[ReferralRewards.id],
                relationId = row[ReferralRewards.relationId],
                referrerId = referrerIdVal,
                referrerName = usersMap[referrerIdVal]?.get(Users.nickname) ?: "用户$referrerIdVal",
                refereeId = refereeIdVal,
                refereeName = if (refereeIdVal > 0)
                    usersMap[refereeIdVal]?.get(Users.nickname) ?: "用户$refereeIdVal" else null,
                rewardType = row[ReferralRewards.rewardType],
                rewardValue = row[ReferralRewards.rewardValue].toDouble(),
                stage = row[ReferralRewards.stage],
                status = row[ReferralRewards.status],
                grantedAt = row[ReferralRewards.grantedAt]?.format(formatter),
                createdAt = row[ReferralRewards.createdAt].format(formatter)
            )
        }

        ReferralRewardsResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    /**
     * 推荐活动汇总统计。
     */
    suspend fun getReferralStatistics(): ReferralStatisticsDto = dbQuery {
        // 有效推荐关系（排除用户自身占位记录）
        val relationRows = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId neq ReferralRelations.refereeId }
            .toList()
        val totalRelations = relationRows.size
        val convertedCount = relationRows.count { it[ReferralRelations.firstConsumedAt] != null }
        val conversionRate = if (totalRelations > 0) {
            String.format("%.2f", convertedCount.toDouble() / totalRelations * 100).toDouble()
        } else 0.0
        val activeReferrerCount = relationRows.map { it[ReferralRelations.referrerId] }.distinct().size

        // 奖励发放统计
        val rewardRows = ReferralRewards.selectAll().toList()
        val grantedRewards = rewardRows.filter { it[ReferralRewards.status] == "granted" }
        val pendingRewardCount = rewardRows.count { it[ReferralRewards.status] == "pending" }
        val totalRewardValue = String.format(
            "%.2f", grantedRewards.sumOf { it[ReferralRewards.rewardValue].toDouble() }
        ).toDouble()

        ReferralStatisticsDto(
            totalRelations = totalRelations,
            convertedCount = convertedCount,
            conversionRate = conversionRate,
            activeReferrerCount = activeReferrerCount,
            grantedRewardCount = grantedRewards.size,
            pendingRewardCount = pendingRewardCount,
            totalRewardValue = totalRewardValue
        )
    }

    /**
     * 管理员手动发放推荐奖励。
     * 校验关系存在且同一阶段未重复发放，按奖励类型发放积分或余额，并记录发放流水。
     */
    suspend fun grantReferralReward(
        request: GrantReferralRewardRequest,
        operator: String
    ): GrantReferralRewardResult = dbQuery {
        val relation = ReferralRelations.selectAll()
            .where { ReferralRelations.id eq request.relationId }
            .toList()
            .singleOrNull()
            ?: return@dbQuery GrantReferralRewardResult.Error("推荐关系不存在")

        val referrerId = relation[ReferralRelations.referrerId]
        val refereeId = relation[ReferralRelations.refereeId]
        val stage = request.stage.ifBlank { "manual" }

        // 防止同一关系、同一阶段重复发放
        val alreadyGranted = ReferralRewards.selectAll()
            .where {
                (ReferralRewards.relationId eq request.relationId) and
                (ReferralRewards.stage eq stage) and
                (ReferralRewards.status eq "granted")
            }
            .count()
        if (alreadyGranted > 0) {
            return@dbQuery GrantReferralRewardResult.Error("该阶段奖励已发放，请勿重复操作")
        }

        val now = LocalDateTime.now()
        val value = request.rewardValue

        when (request.rewardType) {
            "points" -> {
                val points = value.toInt()
                if (points <= 0) {
                    return@dbQuery GrantReferralRewardResult.Error("积分奖励值必须大于0")
                }
                addPointsToUser(
                    userId = referrerId,
                    amount = points,
                    sourceType = "referral_manual_reward",
                    description = "管理员手动发放推荐奖励（操作人：$operator）"
                )
            }
            "balance" -> {
                if (value <= 0) {
                    return@dbQuery GrantReferralRewardResult.Error("余额奖励值必须大于0")
                }
                val wallet = UserWallets.selectAll()
                    .where { UserWallets.userId eq referrerId }
                    .toList()
                    .singleOrNull()
                if (wallet == null) {
                    return@dbQuery GrantReferralRewardResult.Error("推荐人钱包不存在，无法发放余额奖励")
                }
                val currentBonus = wallet[UserWallets.bonusBalance]
                val newBonus = currentBonus.add(value.toBigDecimal())
                UserWallets.update({ UserWallets.userId eq referrerId }) {
                    it[UserWallets.bonusBalance] = newBonus
                    // 与充值/消费/人工调整保持一致：递增版本号并刷新更新时间。
                    // 原先两者都没做，使乐观锁读到的版本与钱包真实状态脱节。
                    it[UserWallets.version] = wallet[UserWallets.version] + 1
                    it[UserWallets.updatedAt] = LocalDateTime.now()
                }
                // 管理员发放的赠送余额同样入资金流水，operatorName 记录是谁发的
                recordWalletTransaction(
                    userId = referrerId,
                    changeType = "referral_reward",
                    amount = BigDecimal.ZERO,
                    balanceBefore = wallet[UserWallets.balance],
                    balanceAfter = wallet[UserWallets.balance],
                    bonusAmount = value.toBigDecimal(),
                    bonusBefore = currentBonus,
                    bonusAfter = newBonus,
                    reason = "管理员手动发放推荐奖励（阶段：$stage）",
                    operatorName = operator,
                    relatedId = request.relationId
                )
            }
            else -> return@dbQuery GrantReferralRewardResult.Error("不支持的奖励类型：${request.rewardType}")
        }

        val rewardId = ReferralRewards.insert {
            it[ReferralRewards.relationId] = request.relationId
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = request.rewardType
            it[ReferralRewards.rewardValue] = value.toBigDecimal()
            it[ReferralRewards.stage] = stage
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = now
        } get ReferralRewards.id

        // 更新推荐关系的奖励状态
        ReferralRelations.update({ ReferralRelations.id eq request.relationId }) {
            it[ReferralRelations.rewardStatus] = "full"
        }

        GrantReferralRewardResult.Success(rewardId)
    }

    /**
     * 构建以某客户为根的推荐链路树（需求 4.4「谁推荐了谁」）。
     *
     * 下行递归：从根开始，逐层列出其直接推荐的被推荐人，最多展开 [maxDepth] 层。
     * 用 visited 集合防环（推荐关系为森林结构，理论上无环，但作为健壮性兜底）。
     *
     * @return 用户不存在时返回 null
     */
    suspend fun getReferralTree(userId: Long, maxDepth: Int = 3): ReferralTreeNode? = dbQuery {
        val rootRow = Users.selectAll()
            .where { Users.id eq userId }
            .toList()
            .singleOrNull()
            ?: return@dbQuery null

        // 一次性加载全量用户与推荐关系，避免递归里逐层 N+1 查询。
        // 推荐关系量级在门店范围内可控，全量加载 + 内存建树比深度递归更稳。
        val allUsers = Users.selectAll().toList()
            .associateBy { it[Users.id] }
        val relationsByReferrer = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId neq ReferralRelations.refereeId }
            .toList()
            .groupBy { it[ReferralRelations.referrerId] }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val visited = mutableSetOf(userId)

        fun buildNode(uid: Long, depth: Int): ReferralTreeNode {
            val row = allUsers[uid]
            val name = row?.get(Users.nickname)
                ?: row?.get(Users.realName)
                ?: "用户$uid"
            val children = if (depth >= maxDepth) {
                emptyList()
            } else {
                (relationsByReferrer[uid] ?: emptyList())
                    .mapNotNull { rel ->
                        val refereeId = rel[ReferralRelations.refereeId]
                        if (refereeId in visited) return@mapNotNull null
                        visited.add(refereeId)
                        buildNode(refereeId, depth + 1).copy(
                            recommendedAt = rel[ReferralRelations.registeredAt].format(formatter),
                            converted = rel[ReferralRelations.firstConsumedAt] != null
                        )
                    }
            }
            return ReferralTreeNode(
                userId = uid,
                name = name,
                phone = row?.get(Users.phone),
                recommendedAt = null,
                converted = false,
                children = children
            )
        }

        buildNode(userId, depth = 0)
    }

    /** 批量加载用户行，构建 id -> ResultRow 映射。 */
    private suspend fun loadUsersMap(userIds: List<Long>): Map<Long, org.jetbrains.exposed.v1.core.ResultRow> {
        if (userIds.isEmpty()) return emptyMap()
        return Users.selectAll()
            .where { Users.id inList userIds }
            .toList()
            .associateBy { it[Users.id] }
    }
}

// DTOs

@Serializable
data class ReferralRelationAdminDto(
    val id: Long,
    val referrerId: Long,
    val referrerName: String,
    val refereeId: Long,
    val refereeName: String,
    val referralCode: String,
    val registeredAt: String,
    val firstConsumedAt: String?,
    val rewardStatus: String
)

@Serializable
data class ReferralRelationsResponse(
    val list: List<ReferralRelationAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class ReferralRewardAdminDto(
    val id: Long,
    val relationId: Long,
    val referrerId: Long,
    val referrerName: String,
    val refereeId: Long,
    val refereeName: String?,
    val rewardType: String,
    val rewardValue: Double,
    val stage: String,
    val status: String,
    val grantedAt: String?,
    val createdAt: String
)

@Serializable
data class ReferralRewardsResponse(
    val list: List<ReferralRewardAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class ReferralStatisticsDto(
    val totalRelations: Int,
    val convertedCount: Int,
    val conversionRate: Double,
    val activeReferrerCount: Int,
    val grantedRewardCount: Int,
    val pendingRewardCount: Int,
    val totalRewardValue: Double
)

/** 推荐链路树节点（需求 4.4）。converted 表示该被推荐人是否已完成首消。 */
@Serializable
data class ReferralTreeNode(
    val userId: Long,
    val name: String,
    val phone: String?,
    /** 作为被推荐人的注册时间；根节点为 null */
    val recommendedAt: String?,
    val converted: Boolean,
    val children: List<ReferralTreeNode>
)

@Serializable
data class GrantReferralRewardRequest(
    val relationId: Long,
    val rewardType: String,          // points/balance
    val rewardValue: Double,
    val stage: String = "manual"     // registration/first_consumption/tier/manual
)

sealed class GrantReferralRewardResult {
    @Serializable
    data class Success(val rewardId: Long) : GrantReferralRewardResult()
    @Serializable
    data class Error(val message: String) : GrantReferralRewardResult()
}
