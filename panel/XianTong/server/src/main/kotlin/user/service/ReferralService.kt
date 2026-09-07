package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Referral service for user growth program.
 */
class ReferralService {

    /**
     * Get or generate user's referral code.
     */
    suspend fun getReferralCode(userId: Long): String = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            // Check if user already has a referral code stored as a referrer
            val existing = ReferralRelations.selectAll()
                .where { ReferralRelations.referrerId eq userId }
                .limit(1)
                .toList()
                .singleOrNull()

            if (existing != null) {
                existing[ReferralRelations.referralCode]
            } else {
                // Generate and persist new referral code
                val code = "REF${userId}${System.currentTimeMillis().toString().takeLast(6)}"
                
                // Store the code by creating a placeholder relation (referrer refers to themselves initially)
                // This allows us to look up the referrer by code later
                ReferralRelations.insert {
                    it[ReferralRelations.referrerId] = userId
                    it[ReferralRelations.refereeId] = userId  // Self-reference as placeholder
                    it[ReferralRelations.referralCode] = code
                    it[ReferralRelations.registeredAt] = LocalDateTime.now()
                    it[ReferralRelations.rewardStatus] = "active"
                }
                
                code
            }
        }
    }

    /**
     * Get user's referral records (people they referred).
     */
    suspend fun getReferralRecords(userId: Long, page: Int, pageSize: Int): ReferralRecordsResponse = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val allRows = ReferralRelations.selectAll()
                .where { ReferralRelations.referrerId eq userId }
                .orderBy(ReferralRelations.registeredAt, SortOrder.DESC)
                .toList()

            // Filter out self-references (placeholder records)
            val rows = allRows.filter { row ->
                row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
            }.let { filtered ->
                val total = filtered.size.toLong()
                val paginated = filtered.drop((page - 1) * pageSize).take(pageSize)
                Pair(total, paginated)
            }

            val total = rows.first
            val paginatedRows = rows.second

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val records = paginatedRows.map { row ->
                // Get referee info
                val referee = Users.selectAll()
                    .where { Users.id eq row[ReferralRelations.refereeId] }
                    .toList()
                    .singleOrNull()

                ReferralRecordDto(
                    id = row[ReferralRelations.id],
                    refereeName = referee?.get(Users.nickname) ?: "用户${row[ReferralRelations.refereeId]}",
                    registeredAt = row[ReferralRelations.registeredAt].format(formatter),
                    firstConsumedAt = row[ReferralRelations.firstConsumedAt]?.format(formatter),
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
    }

    /**
     * Register a new user with referral code.
     */
    suspend fun registerWithReferral(refereeId: Long, referralCode: String): ReferralResult {
        // Pre-checks outside transaction
        val codeRecord = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                ReferralRelations.selectAll()
                    .where { ReferralRelations.referralCode eq referralCode }
                    .toList()
                    .firstOrNull()
            }
        }

        if (codeRecord == null) {
            return ReferralResult.Error("无效的推荐码")
        }

        val referrerId = codeRecord[ReferralRelations.referrerId]

        // Check referrer status
        val referrerStatus = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                Users.selectAll()
                    .where { Users.id eq referrerId }
                    .toList()
                    .singleOrNull()
                    ?.get(Users.status)
            }
        }

        if (referrerStatus == 0) {
            return ReferralResult.Error("推荐人账户已被禁用")
        }

        // Check if already referred
        val alreadyReferred = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                ReferralRelations.selectAll()
                    .where { ReferralRelations.refereeId eq refereeId }
                    .toList()
                    .any { row ->
                        row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
                    }
            }
        }

        if (alreadyReferred) {
            return ReferralResult.Error("该用户已被推荐")
        }

        if (referrerId == refereeId) {
            return ReferralResult.Error("不能使用自己的推荐码")
        }

        // All checks passed, create the relation
        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
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
        }
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
            val expiresAt = LocalDateTime.now().plusMonths(validityMonths.toLong())

            // Add points to referrer
            var currentBalance = UserPointsSummary.selectAll()
                .where { UserPointsSummary.userId eq referrerId }
                .toList()
                .singleOrNull()
                ?.get(UserPointsSummary.totalBalance) ?: 0

            if (currentBalance == 0) {
                currentBalance = PointsRecords.selectAll()
                    .where { PointsRecords.userId eq referrerId }
                    .toList()
                    .sumOf { it[PointsRecords.changeAmount] }
            }

            val newBalance = currentBalance + pointsEarned

            PointsRecords.insert {
                it[PointsRecords.userId] = referrerId
                it[PointsRecords.changeAmount] = pointsEarned
                it[PointsRecords.balanceAfter] = newBalance
                it[PointsRecords.sourceType] = "referral_registration"
                it[PointsRecords.description] = "推荐注册奖励"
                it[PointsRecords.expiresAt] = expiresAt
            }

            val summaryExists = UserPointsSummary.selectAll()
                .where { UserPointsSummary.userId eq referrerId }
                .count() > 0

            if (summaryExists) {
                UserPointsSummary.update({ UserPointsSummary.userId eq referrerId }) {
                    it[UserPointsSummary.totalBalance] = newBalance
                }
            } else {
                UserPointsSummary.insert {
                    it[UserPointsSummary.userId] = referrerId
                    it[UserPointsSummary.totalBalance] = newBalance
                }
            }

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
    suspend fun awardFirstConsumptionReward(refereeId: Long): ReferralResult {
        // Pre-checks outside transaction
        val relationCheck = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                ReferralRelations.selectAll()
                    .where { ReferralRelations.refereeId eq refereeId }
                    .toList()
                    .firstOrNull { row ->
                        row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId]
                    }
            }
        }

        if (relationCheck == null) {
            return ReferralResult.Error("该用户没有推荐关系")
        }

        if (relationCheck[ReferralRelations.firstConsumedAt] != null) {
            return ReferralResult.Error("已发放过首消奖励")
        }

        val referrerId = relationCheck[ReferralRelations.referrerId]

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                val now = LocalDateTime.now()

                // Award 100 points to referrer
                var currentPoints = UserPointsSummary.selectAll()
                    .where { UserPointsSummary.userId eq referrerId }
                    .toList()
                    .singleOrNull()
                    ?.get(UserPointsSummary.totalBalance) ?: 0

                if (currentPoints == 0) {
                    currentPoints = PointsRecords.selectAll()
                        .where { PointsRecords.userId eq referrerId }
                        .toList()
                        .sumOf { it[PointsRecords.changeAmount] }
                }

                val newPoints = currentPoints + 100
                val expiresAt = now.plusMonths(12)

                PointsRecords.insert {
                    it[PointsRecords.userId] = referrerId
                    it[PointsRecords.changeAmount] = 100
                    it[PointsRecords.balanceAfter] = newPoints
                    it[PointsRecords.sourceType] = "referral_first_consumption"
                    it[PointsRecords.description] = "推荐首消奖励-积分"
                    it[PointsRecords.expiresAt] = expiresAt
                }

                val summaryExists = UserPointsSummary.selectAll()
                    .where { UserPointsSummary.userId eq referrerId }
                    .count() > 0

                if (summaryExists) {
                    UserPointsSummary.update({ UserPointsSummary.userId eq referrerId }) {
                        it[UserPointsSummary.totalBalance] = newPoints
                    }
                } else {
                    UserPointsSummary.insert {
                        it[UserPointsSummary.userId] = referrerId
                        it[UserPointsSummary.totalBalance] = newPoints
                    }
                }

                // Award 50 yuan bonus balance
                val currentBonus = UserWallets.selectAll()
                    .where { UserWallets.userId eq referrerId }
                    .toList()
                    .singleOrNull()
                    ?.get(UserWallets.bonusBalance) ?: 0.0.toBigDecimal()

                UserWallets.update({ UserWallets.userId eq referrerId }) {
                    it[UserWallets.bonusBalance] = currentBonus.add(50.0.toBigDecimal())
                }

                // Create referral reward record
                ReferralRewards.insert {
                    it[ReferralRewards.relationId] = relationCheck[ReferralRelations.id]
                    it[ReferralRewards.referrerId] = referrerId
                    it[ReferralRewards.refereeId] = refereeId
                    it[ReferralRewards.rewardType] = "points_balance"
                    it[ReferralRewards.rewardValue] = 150.0.toBigDecimal()
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

                // Award newcomer coupon to referee (100 yuan voucher)
                val newcomerTemplate = CouponTemplates.selectAll()
                    .where { CouponTemplates.name eq "推荐新人券" }
                    .toList()
                    .singleOrNull()

                if (newcomerTemplate != null) {
                    val templateId = newcomerTemplate[CouponTemplates.id]
                    val couponCode = "NEW${refereeId}${System.currentTimeMillis().toString().takeLast(8)}"
                    val validTo = newcomerTemplate[CouponTemplates.validTo]

                    UserCoupons.insert {
                        it[UserCoupons.userId] = refereeId
                        it[UserCoupons.templateId] = templateId
                        it[UserCoupons.couponCode] = couponCode
                        it[UserCoupons.status] = "unused"
                    }

                    // Update issued quantity
                    CouponTemplates.update({ CouponTemplates.id eq templateId }) {
                        it[CouponTemplates.issuedQuantity] = newcomerTemplate[CouponTemplates.issuedQuantity] + 1
                    }
                }

                // Check for tier reward (5+ referrals)
                checkAndAwardTierReward(referrerId)

                ReferralResult.Success
            }
        }
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
            // Check if tier reward already granted
            val tierRewardExists = ReferralRewards.selectAll()
                .where {
                    (ReferralRewards.referrerId eq referrerId) and
                    (ReferralRewards.stage eq "tier")
                }
                .count() > 0

            if (!tierRewardExists) {
                // Award 200 points
                var currentPoints = UserPointsSummary.selectAll()
                    .where { UserPointsSummary.userId eq referrerId }
                    .toList()
                    .singleOrNull()
                    ?.get(UserPointsSummary.totalBalance) ?: 0

                if (currentPoints == 0) {
                    currentPoints = PointsRecords.selectAll()
                        .where { PointsRecords.userId eq referrerId }
                        .toList()
                        .sumOf { it[PointsRecords.changeAmount] }
                }

                val newPoints = currentPoints + 200
                val now = LocalDateTime.now()
                val expiresAt = now.plusMonths(12)

                PointsRecords.insert {
                    it[PointsRecords.userId] = referrerId
                    it[PointsRecords.changeAmount] = 200
                    it[PointsRecords.balanceAfter] = newPoints
                    it[PointsRecords.sourceType] = "referral_tier_reward"
                    it[PointsRecords.description] = "推荐满5人阶梯奖励"
                    it[PointsRecords.expiresAt] = expiresAt
                }

                val summaryExists = UserPointsSummary.selectAll()
                    .where { UserPointsSummary.userId eq referrerId }
                    .count() > 0

                if (summaryExists) {
                    UserPointsSummary.update({ UserPointsSummary.userId eq referrerId }) {
                        it[UserPointsSummary.totalBalance] = newPoints
                    }
                } else {
                    UserPointsSummary.insert {
                        it[UserPointsSummary.userId] = referrerId
                        it[UserPointsSummary.totalBalance] = newPoints
                    }
                }

                // Record tier reward
                ReferralRewards.insert {
                    it[ReferralRewards.relationId] = 0 // No specific relation for tier reward
                    it[ReferralRewards.referrerId] = referrerId
                    it[ReferralRewards.refereeId] = 0
                    it[ReferralRewards.rewardType] = "points"
                    it[ReferralRewards.rewardValue] = 200.0.toBigDecimal()
                    it[ReferralRewards.stage] = "tier"
                    it[ReferralRewards.status] = "granted"
                    it[ReferralRewards.grantedAt] = now
                }
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
