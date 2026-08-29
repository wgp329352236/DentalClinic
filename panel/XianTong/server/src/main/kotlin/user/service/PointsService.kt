package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Points service for user loyalty program.
 */
class PointsService {

    /**
     * Get user's points balance.
     */
    suspend fun getPointsBalance(userId: Long): PointsBalanceDto = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val summary = UserPointsSummary.selectAll()
                .where { UserPointsSummary.userId eq userId }
                .toList()
                .singleOrNull()

            PointsBalanceDto(
                totalBalance = summary?.get(UserPointsSummary.totalBalance) ?: 0,
                updatedAt = summary?.get(UserPointsSummary.updatedAt)?.toString() ?: ""
            )
        }
    }

    /**
     * Get user's points records with pagination.
     */
    suspend fun getPointsRecords(userId: Long, page: Int, pageSize: Int): PointsRecordsResponse = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val total = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .count()

            val rows = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .orderBy(PointsRecords.createdAt, SortOrder.DESC)
                .limit(pageSize).offset((page - 1).toLong())
                .toList()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val records = rows.map { row ->
                PointsRecordDto(
                    id = row[PointsRecords.id],
                    changeAmount = row[PointsRecords.changeAmount],
                    balanceAfter = row[PointsRecords.balanceAfter],
                    sourceType = row[PointsRecords.sourceType],
                    description = row[PointsRecords.description],
                    expiresAt = row[PointsRecords.expiresAt].format(formatter),
                    createdAt = row[PointsRecords.createdAt].format(formatter)
                )
            }

            PointsRecordsResponse(
                list = records,
                total = total,
                page = page,
                pageSize = pageSize
            )
        }
    }

    /**
     * Daily check-in to earn points.
     */
    suspend fun checkIn(userId: Long): PointsResult {
        val today = LocalDate.now()

        // Pre-check outside transaction
        val alreadyCheckedIn = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                PointsRecords.selectAll()
                    .where {
                        (PointsRecords.userId eq userId) and
                        (PointsRecords.sourceType eq "checkin") and
                        (PointsRecords.createdAt greaterEq today.atStartOfDay())
                    }
                    .count() > 0
            }
        }

        if (alreadyCheckedIn) {
            return PointsResult.Error("今日已签到")
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                val rule = PointsRules.selectAll()
                    .where {
                        (PointsRules.actionType eq "signup") and
                        (PointsRules.status eq 1)
                    }
                    .toList()
                    .singleOrNull()

                if (rule == null) {
                    PointsResult.Error("签到规则未配置")
                } else {
                    val pointsEarned = rule[PointsRules.pointsPerUnit]
                    val validityMonths = rule[PointsRules.validityMonths]
                    val expiresAt = LocalDateTime.now().plusMonths(validityMonths.toLong())

                    addPoints(userId, pointsEarned, "checkin", "每日签到奖励", expiresAt)

                    PointsResult.Success(pointsEarned)
                }
            }
        }
    }

    /**
     * Award points for completing profile.
     */
    suspend fun completeProfileReward(userId: Long): PointsResult {
        // Pre-check outside transaction
        val alreadyRewarded = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                PointsRecords.selectAll()
                    .where {
                        (PointsRecords.userId eq userId) and
                        (PointsRecords.sourceType eq "profile_complete")
                    }
                    .count() > 0
            }
        }

        if (alreadyRewarded) {
            return PointsResult.Error("已完成过信息完善奖励")
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                val rule = PointsRules.selectAll()
                    .where {
                        (PointsRules.actionType eq "profile_complete") and
                        (PointsRules.status eq 1)
                    }
                    .toList()
                    .singleOrNull()

                if (rule == null) {
                    PointsResult.Error("奖励规则未配置")
                } else {
                    val pointsEarned = rule[PointsRules.pointsPerUnit]
                    val validityMonths = rule[PointsRules.validityMonths]
                    val expiresAt = LocalDateTime.now().plusMonths(validityMonths.toLong())

                    addPoints(userId, pointsEarned, "profile_complete", "完善个人信息奖励", expiresAt)

                    PointsResult.Success(pointsEarned)
                }
            }
        }
    }

    /**
     * Helper: Add points and update summary.
     */
    private suspend fun addPoints(userId: Long, amount: Int, sourceType: String, description: String, expiresAt: LocalDateTime) {
        // Get current balance from summary or calculate from records
        var currentBalance = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

        // If no summary exists, calculate from records
        if (currentBalance == 0) {
            currentBalance = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .toList()
                .sumOf { it[PointsRecords.changeAmount] }
        }

        val newBalance = currentBalance + amount

        // Insert points record
        PointsRecords.insert {
            it[PointsRecords.userId] = userId
            it[PointsRecords.changeAmount] = amount
            it[PointsRecords.balanceAfter] = newBalance
            it[PointsRecords.sourceType] = sourceType
            it[PointsRecords.description] = description
            it[PointsRecords.expiresAt] = expiresAt
        }

        // Update or insert summary
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

@Serializable
data class PointsBalanceDto(
    val totalBalance: Int,
    val updatedAt: String
)

@Serializable
data class PointsRecordDto(
    val id: Long,
    val changeAmount: Int,
    val balanceAfter: Int,
    val sourceType: String,
    val description: String?,
    val expiresAt: String,
    val createdAt: String
)

@Serializable
data class PointsRecordsResponse(
    val list: List<PointsRecordDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

sealed class PointsResult {
    @Serializable
    data class Success(val pointsEarned: Int) : PointsResult()
    @Serializable
    data class Error(val message: String) : PointsResult()
}
