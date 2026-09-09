package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Admin points management service.
 */
class AdminPointsService : BaseService() {

    /**
     * Get all points rules.
     */
    suspend fun getPointsRules(): List<PointsRuleDto> = dbQuery {
        PointsRules.selectAll()
                .orderBy(PointsRules.id, SortOrder.ASC)
                .toList()
                .map { row ->
                    PointsRuleDto(
                        id = row[PointsRules.id],
                        ruleName = row[PointsRules.ruleName],
                        ruleType = row[PointsRules.ruleType],
                        actionType = row[PointsRules.actionType],
                        pointsPerUnit = row[PointsRules.pointsPerUnit],
                        unitDescription = row[PointsRules.unitDescription],
                        dailyLimit = row[PointsRules.dailyLimit],
                        monthlyLimit = row[PointsRules.monthlyLimit],
                        validityMonths = row[PointsRules.validityMonths],
                        status = row[PointsRules.status]
                    )
                }
    }

    /**
     * Create a new points rule.
     */
    suspend fun createPointsRule(request: CreatePointsRuleRequest): Long = dbQuery {
        PointsRules.insert {
                it[ruleName] = request.ruleName
                it[ruleType] = request.ruleType
                it[actionType] = request.actionType
                it[pointsPerUnit] = request.pointsPerUnit
                it[unitDescription] = request.unitDescription
                it[dailyLimit] = request.dailyLimit
                it[monthlyLimit] = request.monthlyLimit
                it[validityMonths] = request.validityMonths
                it[status] = request.status
            } get PointsRules.id
    }

    /**
     * Update an existing points rule.
     */
    suspend fun updatePointsRule(id: Long, request: UpdatePointsRuleRequest): Unit = dbQuery {
        PointsRules.update({ PointsRules.id eq id }) {
                it[ruleName] = request.ruleName
                it[ruleType] = request.ruleType
                it[actionType] = request.actionType
                it[pointsPerUnit] = request.pointsPerUnit
                it[unitDescription] = request.unitDescription
                it[dailyLimit] = request.dailyLimit
                it[monthlyLimit] = request.monthlyLimit
                it[validityMonths] = request.validityMonths
                it[status] = request.status
            }
    }

    /**
     * Add points to a user. Public API for other admin services to call.
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

    /**
     * Adjust user's points manually (admin operation with validation).
     */
    suspend fun adjustUserPoints(userId: Long, amount: Int, reason: String, operator: String): Unit = dbQuery {
        // Pre-check: validate balance won't go negative
        val currentBalance = getCurrentPointsBalance(userId)
        val newBalance = currentBalance + amount
        if (newBalance < 0) {
            throw IllegalArgumentException("积分余额不足，当前余额：$currentBalance")
        }

        addPoints(
            userId = userId,
            amount = amount,
            sourceType = "admin_adjust",
            description = "管理员手动调整：$reason（操作人：$operator）"
        )
    }

    /** Read current points balance for a user (summary or fallback to records sum). */
    private suspend fun getCurrentPointsBalance(userId: Long): Int {
        val summary = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance)
        if (summary != null && summary != 0) return summary

        return PointsRecords.selectAll()
            .where { PointsRecords.userId eq userId }
            .toList()
            .sumOf { it[PointsRecords.changeAmount] }
    }

    /**
     * Get points records with filters.
     */
    suspend fun getPointsRecords(
        userId: Long?,
        sourceType: String?,
        startDate: String?,
        endDate: String?,
        page: Int,
        pageSize: Int
    ): PointsRecordsResponse = dbQuery {
        var query = PointsRecords.selectAll()

            if (userId != null) {
                query = query.where { PointsRecords.userId eq userId }
            }

            if (sourceType != null) {
                query = query.where { PointsRecords.sourceType eq sourceType }
            }

            if (startDate != null) {
                val startDateTime = LocalDateTime.parse(startDate + "T00:00:00")
                query = query.where { PointsRecords.createdAt greaterEq startDateTime }
            }

            if (endDate != null) {
                val endDateTime = LocalDateTime.parse(endDate + "T23:59:59")
                query = query.where { PointsRecords.createdAt lessEq endDateTime }
            }

            val total = query.count()

            val rows = query
                .orderBy(PointsRecords.createdAt, SortOrder.DESC)
                .limit(pageSize).offset((page - 1).toLong())
                .toList()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val records = rows.map { row ->
                PointsRecordAdminDto(
                    id = row[PointsRecords.id],
                    userId = row[PointsRecords.userId],
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

// DTOs

@Serializable
data class PointsRuleDto(
    val id: Long,
    val ruleName: String,
    val ruleType: String,
    val actionType: String,
    val pointsPerUnit: Int,
    val unitDescription: String?,
    val dailyLimit: Int?,
    val monthlyLimit: Int?,
    val validityMonths: Int,
    val status: Int
)

@Serializable
data class CreatePointsRuleRequest(
    val ruleName: String,
    val ruleType: String,
    val actionType: String,
    val pointsPerUnit: Int,
    val unitDescription: String?,
    val dailyLimit: Int?,
    val monthlyLimit: Int?,
    val validityMonths: Int = 12,
    val status: Int = 1
)

@Serializable
data class UpdatePointsRuleRequest(
    val ruleName: String,
    val ruleType: String,
    val actionType: String,
    val pointsPerUnit: Int,
    val unitDescription: String?,
    val dailyLimit: Int?,
    val monthlyLimit: Int?,
    val validityMonths: Int,
    val status: Int
)

@Serializable
data class PointsAdjustRequest(
    val amount: Int,
    val reason: String
)

@Serializable
data class PointsRecordAdminDto(
    val id: Long,
    val userId: Long,
    val changeAmount: Int,
    val balanceAfter: Int,
    val sourceType: String,
    val description: String?,
    val expiresAt: String,
    val createdAt: String
)

@Serializable
data class PointsRecordsResponse(
    val list: List<PointsRecordAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)
