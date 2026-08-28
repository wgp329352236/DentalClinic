package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Recharge service for user wallet top-up.
 */
class RechargeService {

    /**
     * Create a recharge order and simulate payment success.
     */
    suspend fun createRechargeOrder(userId: Long, clinicId: Long, amount: BigDecimal): RechargeResult {
        // Validate amount
        if (amount < BigDecimal.ONE) {
            return RechargeResult.Error("充值金额不能小于1元")
        }
        if (amount > BigDecimal.valueOf(5000)) {
            return RechargeResult.Error("单笔充值不能超过5000元")
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                // Calculate bonus based on tier rules
                val bonusAmount = calculateBonus(amount)

                // Create recharge record
                val recordId = RechargeRecords.insert {
                    it[RechargeRecords.userId] = userId
                    it[RechargeRecords.clinicId] = clinicId
                    it[RechargeRecords.amount] = amount
                    it[RechargeRecords.bonusAmount] = bonusAmount
                    it[RechargeRecords.paymentMethod] = "wechat"
                    it[RechargeRecords.status] = "pending"
                } get RechargeRecords.id

                // Simulate successful payment
                val paidAt = LocalDateTime.now()
                RechargeRecords.update({ RechargeRecords.id eq recordId }) {
                    it[RechargeRecords.status] = "success"
                    it[RechargeRecords.paidAt] = paidAt
                    it[RechargeRecords.wechatOrderId] = "MOCK_WX_${System.currentTimeMillis()}"
                }

                // Update wallet balance with optimistic locking
                updateWalletBalance(userId, amount, bonusAmount)

                RechargeResult.Success(
                    recordId = recordId,
                    amount = amount.toDouble(),
                    bonusAmount = bonusAmount.toDouble(),
                    totalAdded = amount.add(bonusAmount).toDouble(),
                    paidAt = paidAt.toString()
                )
            }
        }
    }

    /**
     * Calculate bonus amount based on recharge tier rules.
     */
    private fun calculateBonus(amount: BigDecimal): BigDecimal {
        return when {
            amount >= BigDecimal.valueOf(10000) -> amount.multiply(BigDecimal.valueOf(0.25)).setScale(2)
            amount >= BigDecimal.valueOf(5000) -> amount.multiply(BigDecimal.valueOf(0.20)).setScale(2)
            amount >= BigDecimal.valueOf(3000) -> amount.multiply(BigDecimal.valueOf(0.15)).setScale(2)
            amount >= BigDecimal.valueOf(1000) -> amount.multiply(BigDecimal.valueOf(0.10)).setScale(2)
            else -> BigDecimal.ZERO
        }
    }

    /**
     * Update user wallet balance with optimistic locking.
     */
    private suspend fun updateWalletBalance(userId: Long, amount: BigDecimal, bonusAmount: BigDecimal) {
        val currentWallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList()
            .singleOrNull()
            ?: throw IllegalStateException("用户钱包不存在")

        val currentVersion = currentWallet[UserWallets.version]
        val currentBalance = currentWallet[UserWallets.balance]
        val currentBonus = currentWallet[UserWallets.bonusBalance]

        val updated = UserWallets.update({
            (UserWallets.userId eq userId) and (UserWallets.version eq currentVersion)
        }) {
            it[UserWallets.balance] = currentBalance.add(amount)
            it[UserWallets.bonusBalance] = currentBonus.add(bonusAmount)
            it[UserWallets.version] = currentVersion + 1
        }

        if (updated == 0) {
            throw IllegalStateException("余额更新失败，请重试")
        }
    }
}

sealed class RechargeResult {
    data class Success(
        val recordId: Long,
        val amount: Double,
        val bonusAmount: Double,
        val totalAdded: Double,
        val paidAt: String
    ) : RechargeResult()

    data class Error(val message: String) : RechargeResult()
}
