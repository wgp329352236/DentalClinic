package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Recharge service for user wallet top-up.
 */
class RechargeService : BaseService() {

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

        return dbQuery {
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
            val change = updateWalletBalance(userId, amount, bonusAmount)

            // 资金流水：充值是入账，本金与赠送金额分开记账，便于后续对账与退款
            recordWalletTransaction(
                userId = userId,
                changeType = "recharge",
                amount = amount,
                balanceBefore = change.balanceBefore,
                balanceAfter = change.balanceAfter,
                bonusAmount = bonusAmount,
                bonusBefore = change.bonusBefore,
                bonusAfter = change.bonusAfter,
                reason = "充值入账",
                relatedId = recordId
            )

            // 充值积分（需求 3.4.1）
            val pointsEarned = awardRechargePoints(userId, amount, recordId)

            RechargeResult.Success(
                recordId = recordId,
                amount = amount.toDouble(),
                bonusAmount = bonusAmount.toDouble(),
                totalAdded = amount.add(bonusAmount).toDouble(),
                pointsEarned = pointsEarned,
                paidAt = paidAt.toString()
            )
        }
    }

    /**
     * 发放充值积分（需求 3.4.1：每充值 1 元积 0.5 分）。
     *
     * 基数只取实际支付金额，赠送金额不计积分——赠送部分是营销让利，
     * 再给它计积分等于对同一笔钱重复返利。
     *
     * 0.5 分/元 由规则的 unitAmount=2 + pointsPerUnit=1 表达，详见 [BaseService.calculateRulePoints]。
     * 规则未配置或已停用时返回 0，不阻断充值主流程。
     */
    private suspend fun awardRechargePoints(userId: Long, amount: BigDecimal, recordId: Long): Int {
        // 按 id 升序取第一条，避免同 actionType 存在多行时命中结果随查询顺序漂移
        val rule = PointsRules.selectAll()
            .where { (PointsRules.actionType eq "recharge") and (PointsRules.status eq 1) }
            .orderBy(PointsRules.id, SortOrder.ASC)
            .toList()
            .firstOrNull()
            ?: return 0

        val points = calculateRulePoints(amount, rule[PointsRules.pointsPerUnit], rule[PointsRules.unitAmount])
        if (points <= 0) return 0

        addPointsToUser(
            userId = userId,
            amount = points,
            sourceType = "recharge",
            description = "充值奖励",
            validityMonths = rule[PointsRules.validityMonths],
            sourceId = recordId
        )
        return points
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
     *
     * 返回变动前后的四个金额，供调用方写资金流水。
     *
     * 本方法运行在 [createRechargeOrder] 的事务内，失败必须抛异常触发回滚：
     * 若改为返回 RechargeResult.Error，事务会正常提交，留下「充值记录已 success
     * 但钱包未入账」的资金不一致，比报错严重得多。
     */
    private suspend fun updateWalletBalance(
        userId: Long,
        amount: BigDecimal,
        bonusAmount: BigDecimal
    ): WalletChange {
        val currentWallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList()
            .singleOrNull()

        // 钱包缺失时直接开户入账。注册链路（UserAuthService）会建钱包，缺失只可能来自
        // 历史导入或异常数据；原先抛 IllegalStateException 会让这类用户永远无法充值，
        // 并经路由层退化成 500。充值是纯入账，开户即入账不存在余额为负的风险，
        // 与 CustomerService.adjustWallet 的开户策略保持一致。
        if (currentWallet == null) {
            UserWallets.insert {
                it[UserWallets.userId] = userId
                it[balance] = amount
                it[bonusBalance] = bonusAmount
                it[version] = 0
                it[updatedAt] = LocalDateTime.now()
            }
            return WalletChange(
                balanceBefore = BigDecimal.ZERO,
                balanceAfter = amount,
                bonusBefore = BigDecimal.ZERO,
                bonusAfter = bonusAmount
            )
        }

        val currentVersion = currentWallet[UserWallets.version]
        val balanceBefore = currentWallet[UserWallets.balance]
        val bonusBefore = currentWallet[UserWallets.bonusBalance]
        val balanceAfter = balanceBefore.add(amount)
        val bonusAfter = bonusBefore.add(bonusAmount)

        val updated = UserWallets.update({
            (UserWallets.userId eq userId) and (UserWallets.version eq currentVersion)
        }) {
            it[UserWallets.balance] = balanceAfter
            it[UserWallets.bonusBalance] = bonusAfter
            it[UserWallets.version] = currentVersion + 1
            // 该列只有 defaultExpression，不显式赋值则更新时不会刷新
            it[UserWallets.updatedAt] = LocalDateTime.now()
        }

        if (updated == 0) {
            // 乐观锁冲突是可重试的并发问题，不是服务器故障：抛 ConflictException 让路由层回 409，
            // 而非 IllegalStateException 退化成 500（前端无法区分「稍后重试」与「真的坏了」）。
            throw ConflictException("余额正在被其他操作变更，请稍后重试")
        }

        return WalletChange(balanceBefore, balanceAfter, bonusBefore, bonusAfter)
    }
}

/** 钱包一次变动的四个金额快照，仅用于组装资金流水。 */
private data class WalletChange(
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val bonusBefore: BigDecimal,
    val bonusAfter: BigDecimal
)

sealed class RechargeResult {
    data class Success(
        val recordId: Long,
        val amount: Double,
        val bonusAmount: Double,
        val totalAdded: Double,
        /** 本次充值获得的积分；规则未配置或不足一个计分单位时为 0 */
        val pointsEarned: Int,
        val paidAt: String
    ) : RechargeResult()

    data class Error(val message: String) : RechargeResult()
}
