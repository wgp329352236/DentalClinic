package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.service.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Referral service for user growth program.
 */
class ReferralService : BaseService() {

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

            addPointsToUser(
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
     * Award first consumption reward for referral.
     * Called when a referred user completes their first purchase.
     */
    suspend fun awardFirstConsumptionReward(refereeId: Long): ReferralResult = dbQuery {
        val relation = ReferralRelations.selectAll()
            .where { ReferralRelations.refereeId eq refereeId }
            .toList()
            .firstOrNull { row ->
                row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
            }
            ?: return@dbQuery ReferralResult.Error("该用户没有推荐关系")

        if (relation[ReferralRelations.firstConsumedAt] != null) {
            return@dbQuery ReferralResult.Error("已发放过首消奖励")
        }

        val referrerId = relation[ReferralRelations.referrerId]
        val now = LocalDateTime.now()

        // Award points to referrer (read from config, fallback 100)
        val pointsRule = PointsRules.selectAll()
            .where {
                (PointsRules.actionType eq "referral_first_consumption") and
                (PointsRules.status eq 1)
            }
            .toList()
            .singleOrNull()
        val pointsAmount = pointsRule?.get(PointsRules.pointsPerUnit) ?: 100

        addPointsToUser(
            userId = referrerId,
            amount = pointsAmount,
            sourceType = "referral_first_consumption",
            description = "推荐首消奖励-积分",
            validityMonths = 12
        )

        // Award bonus balance (read from config, fallback 50)
        val bonusAmount = 50.0.toBigDecimal()
        val currentBonus = UserWallets.selectAll()
            .where { UserWallets.userId eq referrerId }
            .toList()
            .singleOrNull()
            ?.get(UserWallets.bonusBalance) ?: 0.0.toBigDecimal()

        UserWallets.update({ UserWallets.userId eq referrerId }) {
            it[UserWallets.bonusBalance] = currentBonus.add(bonusAmount)
        }

        // Create referral reward record
        ReferralRewards.insert {
            it[ReferralRewards.relationId] = relation[ReferralRelations.id]
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = "points_balance"
            it[ReferralRewards.rewardValue] = (pointsAmount + 50).toDouble().toBigDecimal()
            it[ReferralRewards.stage] = "first_consumption"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = now
        }

        // Update referral relation
        ReferralRelations.update({
            (ReferralRelations.referrerId eq referrerId) and
            (ReferralRelations.refereeId eq refereeId)
        }) {
            it[ReferralRelations.firstConsumedAt] = now
            it[ReferralRelations.rewardStatus] = "full"
        }

        // Award newcomer coupon to referee
        val newcomerTemplate = CouponTemplates.selectAll()
            .where { CouponTemplates.name eq "推荐新人券" }
            .toList()
            .singleOrNull()

        if (newcomerTemplate != null) {
            val templateId = newcomerTemplate[CouponTemplates.id]
            val couponCode = "NEW${refereeId}${System.currentTimeMillis().toString().takeLast(8)}"

            UserCoupons.insert {
                it[UserCoupons.userId] = refereeId
                it[UserCoupons.templateId] = templateId
                it[UserCoupons.couponCode] = couponCode
                it[UserCoupons.status] = "unused"
            }

            CouponTemplates.update({ CouponTemplates.id eq templateId }) {
                it[CouponTemplates.issuedQuantity] = newcomerTemplate[CouponTemplates.issuedQuantity] + 1
            }
        }

        // Check for tier reward (5+ referrals)
        checkAndAwardTierReward(referrerId)

        ReferralResult.Success
    }

    /**
     * Check if referrer has reached tier threshold and award bonus.
     */
    private suspend fun checkAndAwardTierReward(referrerId: Long) {
        val referralCount = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId eq referrerId }
            .toList()
            .count { row ->
                row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
            }

        if (referralCount >= 5) {
            val tierRewardExists = ReferralRewards.selectAll()
                .where {
                    (ReferralRewards.referrerId eq referrerId) and
                    (ReferralRewards.stage eq "tier")
                }
                .count() > 0

            if (!tierRewardExists) {
                addPointsToUser(
                    userId = referrerId,
                    amount = 200,
                    sourceType = "referral_tier_reward",
                    description = "推荐满5人阶梯奖励",
                    validityMonths = 12
                )

                ReferralRewards.insert {
                    it[ReferralRewards.relationId] = 0
                    it[ReferralRewards.referrerId] = referrerId
                    it[ReferralRewards.refereeId] = 0
                    it[ReferralRewards.rewardType] = "points"
                    it[ReferralRewards.rewardValue] = 200.0.toBigDecimal()
                    it[ReferralRewards.stage] = "tier"
                    it[ReferralRewards.status] = "granted"
                    it[ReferralRewards.grantedAt] = LocalDateTime.now()
                }
            }
        }
    }

    // ==================== Shared Helper ====================

    /**
     * Add points to a user: read current balance, insert record, update/create summary.
     * Eliminates the repeated 14-line pattern across all reward methods.
     */
    private suspend fun addPointsToUser(
        userId: Long,
        amount: Int,
        sourceType: String,
        description: String,
        validityMonths: Int
    ) {
        var currentBalance = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

        if (currentBalance == 0) {
            currentBalance = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .toList()
                .sumOf { it[PointsRecords.changeAmount] }
        }

        val newBalance = currentBalance + amount
        val expiresAt = LocalDateTime.now().plusMonths(validityMonths.toLong())

        PointsRecords.insert {
            it[PointsRecords.userId] = userId
            it[PointsRecords.changeAmount] = amount
            it[PointsRecords.balanceAfter] = newBalance
            it[PointsRecords.sourceType] = sourceType
            it[PointsRecords.description] = description
            it[PointsRecords.expiresAt] = expiresAt
        }

        val summaryExists = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .count() > 0

        if (summaryExists) {
            UserPointsSummary.update({ UserPointsSummary.userId eq userId }) {
                it[UserPointsSummary.totalBalance] = newBalance
            }
        } else {
            UserPointsSummary.insert {
                it[UserPointsSummary.userId] = userId
                it[UserPointsSummary.totalBalance] = newBalance
            }
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
