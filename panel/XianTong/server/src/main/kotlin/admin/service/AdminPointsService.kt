package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
        assertActionTypeAvailable(request.actionType)
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
        val exists = PointsRules.selectAll()
            .where { PointsRules.id eq id }
            .count() > 0
        if (!exists) throw NotFoundException("积分规则不存在")
        assertActionTypeAvailable(request.actionType, excludeId = id)
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
     * 校验 actionType 尚未被其它规则占用。
     *
     * points_rule.action_type 上有唯一索引（见 SchemaMigration），直接撞索引会抛数据库异常变成 500，
     * 这里提前拦下给出可读的 409。业务语义是「一个 actionType 只能对应一条规则」，
     * 多行时消费/推荐等链路取哪一条取决于查询顺序，积分额度会漂移。
     *
     * @param excludeId 更新场景下排除自身
     */
    private suspend fun assertActionTypeAvailable(actionType: String, excludeId: Long? = null) {
        val conflict = PointsRules.selectAll()
            .where { PointsRules.actionType eq actionType }
            .toList()
            .firstOrNull { it[PointsRules.id] != excludeId }
        if (conflict != null) {
            throw ConflictException(
                "积分规则类型「$actionType」已存在（规则ID ${conflict[PointsRules.id]}），同一类型只能配置一条"
            )
        }
    }

    /**
     * Add points to a user. Public API for other admin services to call.
     * Delegates to BaseService.addPointsToUser for the core logic.
     *
     * @param sourceId 业务来源ID（如消费记录ID），写入积分流水便于溯源
     * @return the new balance after adjustment
     */
    suspend fun addPoints(
        userId: Long,
        amount: Int,
        sourceType: String,
        description: String,
        validityMonths: Int = 12,
        sourceId: Long? = null
    ): Int = addPointsToUser(userId, amount, sourceType, description, validityMonths, sourceId)

    /**
     * Adjust user's points manually (admin operation with validation).
     */
    suspend fun adjustUserPoints(userId: Long, amount: Int, reason: String, operator: String): Unit = dbQuery {
        // Pre-check: validate balance won't go negative
        val currentBalance = getCurrentPointsBalance(userId)
        val newBalance = currentBalance + amount
        if (newBalance < 0) {
            // 积分不足是可预期的业务校验，必须抛 ValidationException 走 400：
            // 抛 IllegalArgumentException 会落入 StatusPages 的 exception<Exception> 兜底，
            // 管理员只看到「服务器内部错误」而丢失了余额提示，日志还被记成未处理异常。
            throw ValidationException("积分余额不足，当前余额：$currentBalance")
        }

        addPoints(
            userId = userId,
            amount = amount,
            sourceType = "admin_adjust",
            description = "管理员手动调整：$reason（操作人：$operator）"
        )
    }

    /**
     * 解析 yyyy-MM-dd 的日期筛选参数。
     *
     * 格式非法时抛 ValidationException（400），而不是让 DateTimeParseException
     * 落入 500 兜底：传错日期是调用方的输入问题，不是服务器故障。
     *
     * @param endOfDay true 补 23:59:59（含当天），false 补 00:00:00
     */
    private fun parseDateFilter(date: String, endOfDay: Boolean): LocalDateTime =
        try {
            LocalDateTime.parse(date + if (endOfDay) "T23:59:59" else "T00:00:00")
        } catch (e: DateTimeParseException) {
            throw ValidationException("日期格式应为 yyyy-MM-dd，收到：$date")
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
                query = query.andWhere { PointsRecords.userId eq userId }
            }

            if (sourceType != null) {
                query = query.andWhere { PointsRecords.sourceType eq sourceType }
            }

            if (startDate != null) {
                val startDateTime = parseDateFilter(startDate, endOfDay = false)
                query = query.andWhere { PointsRecords.createdAt greaterEq startDateTime }
            }

            if (endDate != null) {
                val endDateTime = parseDateFilter(endDate, endOfDay = true)
                query = query.andWhere { PointsRecords.createdAt lessEq endDateTime }
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
