package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.security.PhoneCipherService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Points service for user loyalty program.
 * Provides both user-facing operations (check-in, records) and shared points adjustment.
 */
class PointsService : BaseService() {

    /**
     * Get user's points balance.
     */
    suspend fun getPointsBalance(userId: Long): PointsBalanceDto = dbQuery {
        val summary = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()

        PointsBalanceDto(
            totalBalance = summary?.get(UserPointsSummary.totalBalance) ?: 0,
            updatedAt = summary?.get(UserPointsSummary.updatedAt)?.toString() ?: ""
        )
    }

    /**
     * Get user's points records with pagination.
     */
    suspend fun getPointsRecords(userId: Long, page: Int, pageSize: Int): PointsRecordsResponse = dbQuery {
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

    /**
     * 积分汇总（需求 3.4.3）：本月获取/消耗 + 积分有效期分布。
     *
     * - monthlyEarned/monthlySpent：按当月（自然月）所有记录汇总，消耗取绝对值；
     * - expiryGroups：有效期分布采用 FIFO 冲抵——积分消耗（兑换/抵扣/手动扣减）不按批次
     *   归因，故先统计真实消费量（排除 points_expired 过期清零），再按获得时间序依次冲抵
     *   最早的未过期批次，剩余的按到期月份聚合升序返回。保证 sum(expiryGroups) 恒等于
     *   当前可用余额对应的未过期积分，不再虚高。
     */
    suspend fun getPointsSummary(userId: Long): PointsSummaryDto = dbQuery {
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()
        val now = LocalDateTime.now()
        val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        val allRows = PointsRecords.selectAll()
            .where { PointsRecords.userId eq userId }
            .toList()

        val monthlyEarned = allRows
            .filter { it[PointsRecords.createdAt] >= monthStart && it[PointsRecords.changeAmount] > 0 }
            .sumOf { it[PointsRecords.changeAmount] }
        val monthlySpent = allRows
            .filter { it[PointsRecords.createdAt] >= monthStart && it[PointsRecords.changeAmount] < 0 }
            .sumOf { Math.abs(it[PointsRecords.changeAmount]) }

        // 有效期分布（FIFO 冲抵）：积分消耗不按批次归因，先统计「真实消费量」
        // （含兑换/抵扣/手动扣减，排除 points_expired 过期清零——它已按批次全额标记，
        // 从 expiredAmount 体现，无需再参与冲抵），再按获得时间序冲抵最早的未过期批次。
        val consumed = allRows
            .filter { it[PointsRecords.changeAmount] < 0 && it[PointsRecords.sourceType] != "points_expired" }
            .sumOf { Math.abs(it[PointsRecords.changeAmount]) }

        val pending = allRows
            .filter { it[PointsRecords.changeAmount] > 0 && it[PointsRecords.expiresAt] > now }
            .sortedBy { it[PointsRecords.createdAt] }
            .map { row ->
                // 未过期批次尚未被清零，remaining 即当前仍有可用潜力
                val remaining = row[PointsRecords.changeAmount] - row[PointsRecords.expiredAmount]
                row to remaining
            }

        val expiryMap = linkedMapOf<String, Int>()
        var restConsumed = consumed
        pending.forEach { (row, remaining) ->
            if (remaining <= 0) return@forEach
            val deducted = minOf(remaining, restConsumed)
            val net = remaining - deducted
            restConsumed -= deducted
            if (net > 0) {
                val month = row[PointsRecords.expiresAt].format(monthFormatter)
                expiryMap[month] = (expiryMap[month] ?: 0) + net
            }
        }
        val expiryGroups = expiryMap
            .map { (month, points) -> ExpiryGroup(expireMonth = month, points = points) }
            .sortedBy { it.expireMonth }

        PointsSummaryDto(
            totalBalance = allRows.sumOf { it[PointsRecords.changeAmount] },
            monthlyEarned = monthlyEarned,
            monthlySpent = monthlySpent,
            expiryGroups = expiryGroups
        )
    }

    /**
     * Daily check-in to earn points.
     */
    suspend fun checkIn(userId: Long): PointsResult = dbQuery {
        val today = LocalDate.now()

        val alreadyCheckedIn = PointsRecords.selectAll()
            .where {
                (PointsRecords.userId eq userId) and
                (PointsRecords.sourceType eq "checkin") and
                (PointsRecords.createdAt greaterEq today.atStartOfDay())
            }
            .count() > 0

        if (alreadyCheckedIn) {
            return@dbQuery PointsResult.Error("今日已签到")
        }

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

            addPoints(userId, pointsEarned, "checkin", "每日签到奖励", validityMonths)

            PointsResult.Success(pointsEarned)
        }
    }

    /**
     * Award points for completing profile.
     */
    suspend fun completeProfileReward(userId: Long): PointsResult = dbQuery {
        // 锁用户行后再判重：并发双击/重试会串行化，配合部分唯一索引形成双保险。
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .forUpdate()
            .toList()
            .singleOrNull()
            ?: return@dbQuery PointsResult.Error("用户不存在")

        val alreadyRewarded = PointsRecords.selectAll()
            .where {
                (PointsRecords.userId eq userId) and
                (PointsRecords.sourceType eq "profile_complete")
            }
            .count() > 0

        if (alreadyRewarded) {
            return@dbQuery PointsResult.Success(0)
        }

        val phone = PhoneCipherService.readPhone(user[Users.phoneCipher], user[Users.phone])
        if (user[Users.realName].isNullOrBlank() ||
            phone.isNullOrBlank() ||
            user[Users.gender] == null ||
            user[Users.age] == null ||
            user[Users.primaryClinicId] == null
        ) {
            return@dbQuery PointsResult.Error("请先完善姓名、手机号、性别、年龄和归属门店")
        }

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

            addPoints(userId, pointsEarned, "profile_complete", "完善个人信息奖励", validityMonths)

            PointsResult.Success(pointsEarned)
        }
    }

    /**
     * Add points to a user. Public API for other services to call.
     * Delegates to BaseService.addPointsToUser for the core logic.
     *
     * @return the new balance after adjustment
     */
    suspend fun addPoints(
        userId: Long,
        amount: Int,
        sourceType: String,
        description: String,
        validityMonths: Int = 12
    ): Int = addPointsToUser(userId, amount, sourceType, description, validityMonths)
}

// DTOs

@Serializable
data class PointsBalanceDto(
    val totalBalance: Int,
    val updatedAt: String
)

@Serializable
data class PointsSummaryDto(
    val totalBalance: Int,
    val monthlyEarned: Int,
    val monthlySpent: Int,
    val expiryGroups: List<ExpiryGroup>
)

@Serializable
data class ExpiryGroup(
    val expireMonth: String,
    val points: Int
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
