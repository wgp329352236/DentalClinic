package com.dentalclinic.common

import com.dentalclinic.admin.entity.Notifications
import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.entity.WalletTransactions
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

open class BaseService {

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction(DBConfig.database) { block() }

    /**
     * Add points to a user: read current balance, insert record, update/create summary.
     * Shared across all services that need to adjust user points.
     * 
     * NOTE: This is a plain suspend function that operates within the caller's transaction.
     * Callers should ensure they are within a transaction context (dbQuery block).
     *
     * @param sourceId 业务来源ID（如消费记录ID、充值记录ID），用于积分流水溯源
     * @return the new balance after adjustment
     */
    suspend fun addPointsToUser(
        userId: Long,
        amount: Int,
        sourceType: String,
        description: String,
        validityMonths: Int = 12,
        sourceId: Long? = null
    ): Int {
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
            it[PointsRecords.sourceId] = sourceId
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

        return newBalance
    }

    /**
     * 按积分规则比率计算应发积分。
     *
     * 比率由「每 unitAmount 个单位积 pointsPerUnit 分」表达，使「每充值 1 元积 0.5 分」
     * 这类小数积分率无需把整型列改成 decimal 就能精确表示（unitAmount=2、pointsPerUnit=1）。
     * 不足一个计分单位的部分向下取整丢弃，与消费积分的既有口径一致。
     *
     * 纯函数，不访问数据库，供各业务服务在自己的事务内调用。
     */
    fun calculateRulePoints(baseAmount: BigDecimal, pointsPerUnit: Int, unitAmount: Int): Int {
        if (pointsPerUnit <= 0 || unitAmount <= 0 || baseAmount <= BigDecimal.ZERO) return 0
        return baseAmount
            .multiply(BigDecimal(pointsPerUnit))
            .divide(BigDecimal(unitAmount), 0, RoundingMode.DOWN)
            .toInt()
    }

    /**
     * 写入一条钱包资金流水。
     *
     * 与 [addPointsToUser] 同为事务内方法（plain suspend，不自行开启事务），
     * 调用方需在余额更新成功后紧接着调用，使流水与钱包状态原子提交。
     *
     * 资金凭证不进 admin_audit_log：后者由 AdminCleanupService 每日清理 90 天前的记录，
     * 而充值/消费/人工调整的账目需长期可追溯。
     *
     * @param changeType recharge/consume/adjust/refund
     * @param amount 本金余额变动（正=入账，负=出账）
     * @param bonusAmount 赠送余额变动
     * @param operatorName 操作员（管理端账号）；C 端自助操作传 null
     * @param relatedId 关联业务ID（充值记录ID / 消费记录ID）
     * @return 新流水ID
     */
    suspend fun recordWalletTransaction(
        userId: Long,
        changeType: String,
        amount: BigDecimal,
        balanceBefore: BigDecimal,
        balanceAfter: BigDecimal,
        bonusAmount: BigDecimal = BigDecimal.ZERO,
        bonusBefore: BigDecimal = BigDecimal.ZERO,
        bonusAfter: BigDecimal = BigDecimal.ZERO,
        reason: String? = null,
        operatorName: String? = null,
        relatedId: Long? = null
    ): Long = WalletTransactions.insert {
        it[WalletTransactions.userId] = userId
        it[WalletTransactions.changeType] = changeType
        it[WalletTransactions.amount] = amount
        it[WalletTransactions.bonusAmount] = bonusAmount
        it[WalletTransactions.balanceBefore] = balanceBefore
        it[WalletTransactions.balanceAfter] = balanceAfter
        it[WalletTransactions.bonusBefore] = bonusBefore
        it[WalletTransactions.bonusAfter] = bonusAfter
        it[WalletTransactions.reason] = reason
        it[WalletTransactions.operatorName] = operatorName
        it[WalletTransactions.relatedId] = relatedId
    } get WalletTransactions.id

    /**
     * 写入一条站内消息。
     *
     * 与 [addPointsToUser] 同为事务内方法（plain suspend，不自行开启事务），
     * 以便业务方把「消息通知」与主流程放在同一事务内原子提交。
     * 需要独立事务时请改用 `user.service.NotificationService.createNotification`。
     *
     * @param msgType system/consumption/activity
     * @param relatedId 关联业务ID（如消费记录ID）
     * @return 新消息ID
     */
    suspend fun sendNotification(
        userId: Long,
        title: String,
        content: String,
        msgType: String = "system",
        relatedId: Long? = null
    ): Long = Notifications.insert {
        it[Notifications.userId] = userId
        it[Notifications.title] = title
        it[Notifications.content] = content
        it[Notifications.msgType] = msgType
        it[Notifications.isRead] = 0
        it[Notifications.relatedId] = relatedId
    } get Notifications.id
}
