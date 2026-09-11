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
     * Get or generate user's referral code.
     */
    suspend fun getReferralCode(userId: Long): String = dbQuery {
        val existing = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId eq userId }
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
            ReferralRecordDto(
                id = row[ReferralRelations.id],
                refereeName = referee?.get(Users.nickname) ?: "用户${row[ReferralRelations.refereeId]}",
                registeredAt = row[ReferralRelations.registeredAt].format(dateTimeFormatter),
                firstConsumedAt = row[ReferralRelations.firstConsumedAt]?.format(dateTimeFormatter),
                rewardStatus = row[ReferralRelations.rewardStatus]
            )
        }

        ReferralRecordsResponse(
            list = records,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    /**
     * Register a new user with referral code.
     * Single atomic transaction — all checks and inserts are consistent.
     */
    suspend fun registerWithReferral(refereeId: Long, referralCode: String): ReferralResult = dbQuery {
        // Validate referral code
        val codeRecord = ReferralRelations.selectAll()
            .where { ReferralRelations.referralCode eq referralCode }
            .toList()
            .firstOrNull()
            ?: return@dbQuery ReferralResult.Error("无效的推荐码")

        val referrerId = codeRecord[ReferralRelations.referrerId]

        // Check referrer status
        val referrerStatus = Users.selectAll()
            .where { Users.id eq referrerId }
            .toList()
            .singleOrNull()
            ?.get(Users.status)

        if (referrerStatus == 0) {
            return@dbQuery ReferralResult.Error("推荐人账户已被禁用")
        }

        if (referrerId == refereeId) {
            return@dbQuery ReferralResult.Error("不能使用自己的推荐码")
        }

        // Check if already referred
        val alreadyReferred = ReferralRelations.selectAll()
            .where { ReferralRelations.refereeId eq refereeId }
            .toList()
            .any { row ->
                row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
            }

        if (alreadyReferred) {
            return@dbQuery ReferralResult.Error("该用户已被推荐")
        }

        // All checks passed, create the relation
        ReferralRelations.insert {
            it[ReferralRelations.referrerId] = referrerId
            it[ReferralRelations.refereeId] = refereeId
            it[ReferralRelations.referralCode] = referralCode
            it[ReferralRelations.registeredAt] = LocalDateTime.now()
            it[ReferralRelations.rewardStatus] = "pending"
        }

        awardRegistrationReward(referrerId, refereeId)
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
    val rewardStatus: String
)

data class ReferralRecordsResponse(
    val list: List<ReferralRecordDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

sealed class ReferralResult {
    object Success : ReferralResult()
    data class Error(val message: String) : ReferralResult()
}
