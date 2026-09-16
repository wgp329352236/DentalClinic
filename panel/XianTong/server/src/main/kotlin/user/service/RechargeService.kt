package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.RechargeTierService
import com.dentalclinic.common.wx.JsapiPayParams
import com.dentalclinic.common.wx.WechatConfig
import com.dentalclinic.common.wx.WechatPayClient
import com.dentalclinic.common.wx.WechatPayException
import com.dentalclinic.common.wx.WxPayNotification
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Recharge service for user wallet top-up.
 *
 * 支付模式：
 * - 已配置微信支付（WechatConfig.pay.enabled）：创建 pending 订单 + 统一下单返回 JSAPI
 *   调起参数，真正入账只发生在「微信异步回调验签通过」或「主动查单确认 SUCCESS」；
 * - 未配置时降级为 mock：下单即支付成功并直接入账，仅供本地联调（启动日志有 WARN）。
 */
class RechargeService(
    // 充值赠送规则改读 recharge_tier 配置表（表空/异常时服务内部回退内置 4 档）
    private val tierService: RechargeTierService = RechargeTierService()
) : BaseService() {

    private val logger = LoggerFactory.getLogger("RechargeService")

    /** 启用中的充值档位（C 端档位展示与下单算赠送共用同一数据源）。 */
    suspend fun listTiers(): List<RechargeTierService.Tier> = tierService.listEnabledTiers()

    /**
     * 创建充值订单。
     *
     * @return [RechargeResult.Pending] 待调起微信支付；[RechargeResult.Success] mock 模式已入账；
     *         [RechargeResult.Error] 参数/下单错误
     */
    suspend fun createRechargeOrder(userId: Long, clinicId: Long, amount: BigDecimal): RechargeResult {
        // Validate amount
        if (amount < BigDecimal.ONE) {
            return RechargeResult.Error("充值金额不能小于1元")
        }
        if (amount > BigDecimal.valueOf(5000)) {
            return RechargeResult.Error("单笔充值不能超过5000元")
        }
        // 金额必须与 DECIMAL(12,2) 对齐，否则元转分无法精确换算，回调金额校验会失败
        if (amount.stripTrailingZeros().scale() > 2) {
            return RechargeResult.Error("充值金额最多保留两位小数")
        }

        if (!WechatConfig.pay.enabled) {
            return createMockOrder(userId, clinicId, amount)
        }

        // 赠送金额在事务外按当前启用档位算好（档位读取自带独立事务，不能嵌套进下单事务）
        val bonusAmount = tierService.calculateBonus(amount)

        // 1) 事务内创建 pending 订单并读取 openid（HTTP 下单在事务外，避免长事务占连接）
        val order = dbQuery {
            val outTradeNo = generateOutTradeNo()
            val recordId = RechargeRecords.insert {
                it[RechargeRecords.userId] = userId
                it[RechargeRecords.clinicId] = clinicId
                it[RechargeRecords.amount] = amount
                it[RechargeRecords.bonusAmount] = bonusAmount
                it[RechargeRecords.paymentMethod] = "wechat"
                it[RechargeRecords.outTradeNo] = outTradeNo
                it[RechargeRecords.status] = "pending"
            } get RechargeRecords.id

            val openid = Users.selectAll()
                .where { Users.id eq userId }
                .toList()
                .singleOrNull()
                ?.get(Users.openid)

            PendingOrderContext(recordId, amount, bonusAmount, outTradeNo, openid)
        }

        if (order.openid.isNullOrBlank()) {
            // 正常注册链路一定写 openid；缺失只可能是脏数据，拒绝下单而不是用空 openid 调微信
            logger.error("[Recharge]: 用户 $userId 缺少 openid，无法发起微信支付")
            return RechargeResult.Error("账户信息异常，请重新登录后再试")
        }

        // 2) 调微信统一下单（事务外）。失败把订单置 failed，该 out_trade_no 作废，用户重试会生成新单
        val amountFen = order.amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
        val prepayId = try {
            WechatPayClient.createJsapiOrder(
                outTradeNo = order.outTradeNo,
                amountFen = amountFen,
                description = "门诊账户充值",
                openid = order.openid
            )
        } catch (e: Exception) {
            logger.warn("[Recharge]: 用户 $userId 统一下单失败 ${order.outTradeNo}: ${e.message}")
            markOrderFailed(order.recordId)
            return RechargeResult.Error("微信支付下单失败，请稍后重试")
        }

        // 3) 回写 prepay_id 并组装小程序调起参数（本地签名，无网络调用）
        dbQuery {
            RechargeRecords.update({ RechargeRecords.id eq order.recordId }) {
                it[RechargeRecords.prepayId] = prepayId
            }
        }

        return RechargeResult.Pending(
            recordId = order.recordId,
            amount = order.amount.toDouble(),
            bonusAmount = order.bonusAmount.toDouble(),
            outTradeNo = order.outTradeNo,
            payParams = WechatPayClient.buildJsapiPayParams(prepayId)
        )
    }

    /**
     * 处理微信支付结果异步通知（回调路由已完成验签与解密后调用）。
     *
     * 安全与幂等：
     * - 只信回调金额（分）与订单金额精确一致，不一致直接拒绝（疑似伪造/串单）；
     * - pending → success 的翻转带状态条件，微信重复推送或与主动查单并发时只入账一次；
     * - 订单已 success 的重复通知直接 ACK，避免微信反复重试。
     *
     * @return true 时路由回 {"code":"SUCCESS"}；false 回 FAIL/非 2xx，微信会按策略重试
     */
    suspend fun handlePayNotification(notification: WxPayNotification): Boolean {
        return try {
            dbQuery {
                val record = RechargeRecords.selectAll()
                    .where { RechargeRecords.outTradeNo eq notification.outTradeNo }
                    .toList()
                    .singleOrNull()

                if (record == null) {
                    logger.error(
                        "[Recharge]: 支付回调找不到订单 out_trade_no=${notification.outTradeNo}, " +
                            "transaction_id=${notification.transactionId}"
                    )
                    return@dbQuery false
                }

                val expectedFen = record[RechargeRecords.amount]
                    .movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact()
                if (notification.totalFen != expectedFen) {
                    // 金额不符是最高风险信号：不入账、不 ACK，人工介入前微信会持续重试
                    logger.error(
                        "[Recharge]: 支付回调金额不符 out_trade_no=${notification.outTradeNo} " +
                            "期望=${expectedFen}分 回调=${notification.totalFen}分"
                    )
                    return@dbQuery false
                }

                when (record[RechargeRecords.status]) {
                    "success" -> {
                        // 幂等：重复通知，什么都不做
                        logger.info("[Recharge]: 订单 ${notification.outTradeNo} 已入账，忽略重复回调")
                        true
                    }
                    "pending" -> {
                        if (notification.tradeState == "SUCCESS") {
                            creditIfPending(
                                recordId = record[RechargeRecords.id],
                                userId = record[RechargeRecords.userId],
                                amount = record[RechargeRecords.amount],
                                bonusAmount = record[RechargeRecords.bonusAmount],
                                transactionId = notification.transactionId.takeIf { it.isNotBlank() },
                                paidAt = LocalDateTime.now()
                            )
                            logger.info(
                                "[Recharge]: 回调入账成功 out_trade_no=${notification.outTradeNo} " +
                                    "amount=${record[RechargeRecords.amount]}"
                            )
                        } else {
                            // NOTPAY/CLOSED/PAYERROR 等非成功状态：ACK 但不入账，等成功状态的通知
                            logger.info(
                                "[Recharge]: 订单 ${notification.outTradeNo} 回调状态 " +
                                    "${notification.tradeState}，暂不入账"
                            )
                        }
                        true
                    }
                    else -> {
                        // failed/refunded 订单收到回调：ACK 但不做任何资金操作
                        logger.warn(
                            "[Recharge]: 订单 ${notification.outTradeNo} 状态为 " +
                                "${record[RechargeRecords.status]}，忽略回调（${notification.tradeState}）"
                        )
                        true
                    }
                }
            }
        } catch (e: Exception) {
            // 入账过程异常（如乐观锁冲突、DB 抖动）：返回 FAIL 让微信稍后重试，保证最终一致
            logger.error("[Recharge]: 处理支付回调异常 ${notification.outTradeNo}", e)
            false
        }
    }

    /**
     * 查询充值订单状态；pending 订单会主动向微信查单兜底（回调延迟/丢失时前端轮询用）。
     *
     * @return 订单不存在或不属于该用户时返回 null（路由回 404）
     */
    suspend fun getRechargeStatus(recordId: Long, userId: Long): RechargeStatusInfo? {
        var record = dbQuery {
            RechargeRecords.selectAll()
                .where { (RechargeRecords.id eq recordId) and (RechargeRecords.userId eq userId) }
                .toList()
                .singleOrNull()
        } ?: return null

        // mock 模式订单在下单时已入账；真实支付下 pending 才需要主动查单
        if (record[RechargeRecords.status] == "pending" && WechatConfig.pay.enabled) {
            val outTradeNo = record[RechargeRecords.outTradeNo]
            if (!outTradeNo.isNullOrBlank()) {
                val state = try {
                    WechatPayClient.queryOrderByOutTradeNo(outTradeNo)
                } catch (e: WechatPayException) {
                    // 查单失败不致命：保留 pending，等下次轮询或异步回调
                    logger.warn("[Recharge]: 主动查单失败 $outTradeNo: ${e.message}")
                    null
                }

                if (state != null) {
                    when (state.tradeState) {
                        "SUCCESS" -> dbQuery {
                            val fresh = RechargeRecords.selectAll()
                                .where { RechargeRecords.id eq recordId }
                                .toList()
                                .singleOrNull()
                            if (fresh != null && fresh[RechargeRecords.status] == "pending") {
                                creditIfPending(
                                    recordId = fresh[RechargeRecords.id],
                                    userId = fresh[RechargeRecords.userId],
                                    amount = fresh[RechargeRecords.amount],
                                    bonusAmount = fresh[RechargeRecords.bonusAmount],
                                    transactionId = state.transactionId,
                                    paidAt = LocalDateTime.now()
                                )
                                logger.info("[Recharge]: 主动查单确认支付并入账 $outTradeNo")
                            }
                        }
                        // 用户已不可能再完成支付的终态：置 failed，前端引导重新下单
                        "CLOSED", "REVOKED", "PAYERROR" -> dbQuery {
                            RechargeRecords.update({
                                (RechargeRecords.id eq recordId) and
                                    (RechargeRecords.status eq "pending")
                            }) {
                                it[RechargeRecords.status] = "failed"
                            }
                        }
                        // NOTPAY/USERPAYING：继续等
                    }
                }

                record = dbQuery {
                    RechargeRecords.selectAll()
                        .where { RechargeRecords.id eq recordId }
                        .toList()
                        .single()
                }
            }
        }

        val status = record[RechargeRecords.status]
        val amount = record[RechargeRecords.amount]
        val bonus = record[RechargeRecords.bonusAmount]
        // 赠送与积分在入账前不向前端暴露确定值（虽然下单时已算好，未支付不应被视为所得）
        val paidAt = record[RechargeRecords.paidAt]
        return RechargeStatusInfo(
            recordId = recordId,
            status = status,
            amount = amount.toDouble(),
            bonusAmount = bonus.toDouble(),
            totalAdded = if (status == "success") amount.add(bonus).toDouble() else null,
            pointsEarned = null,
            paidAt = paidAt?.toString()
        )
    }

    /**
     * mock 模式：未配置微信支付时，下单即支付成功并入账（仅限本地/演示环境）。
     */
    private suspend fun createMockOrder(userId: Long, clinicId: Long, amount: BigDecimal): RechargeResult.Success {
        val paidAt = LocalDateTime.now()
        // 赠送金额在事务外按当前启用档位算好（档位读取自带独立事务，不能嵌套）
        val bonusAmount = tierService.calculateBonus(amount)
        return dbQuery {
            val outTradeNo = generateOutTradeNo()
            val recordId = RechargeRecords.insert {
                it[RechargeRecords.userId] = userId
                it[RechargeRecords.clinicId] = clinicId
                it[RechargeRecords.amount] = amount
                it[RechargeRecords.bonusAmount] = bonusAmount
                it[RechargeRecords.paymentMethod] = "wechat"
                it[RechargeRecords.outTradeNo] = outTradeNo
                it[RechargeRecords.status] = "pending"
            } get RechargeRecords.id

            creditIfPending(
                recordId = recordId,
                userId = userId,
                amount = amount,
                bonusAmount = bonusAmount,
                transactionId = "MOCK_WX_${System.currentTimeMillis()}",
                paidAt = paidAt
            )

            // mock 下单是同步入账，积分在同一事务内已算出，重放一次纯计算用于响应体
            val pointsEarned = calculateRechargePoints(amount)
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

    private suspend fun markOrderFailed(recordId: Long) {
        dbQuery {
            RechargeRecords.update({
                (RechargeRecords.id eq recordId) and (RechargeRecords.status eq "pending")
            }) {
                it[RechargeRecords.status] = "failed"
            }
        }
    }

    /**
     * 入账唯一入口：条件翻转 pending → success，翻转成功才动钱包/流水/积分。
     *
     * 条件更新保证三条入账路径（mock 下单、异步回调、主动查单）并发时只有一条生效，
     * 从库层面杜绝重复入账。运行在调用方事务内，任何异常都会随事务整体回滚。
     *
     * @return true 本次完成入账；false 订单已不是 pending（被并发路径抢先处理）
     */
    private suspend fun creditIfPending(
        recordId: Long,
        userId: Long,
        amount: BigDecimal,
        bonusAmount: BigDecimal,
        transactionId: String?,
        paidAt: LocalDateTime
    ): Boolean {
        val flipped = RechargeRecords.update({
            (RechargeRecords.id eq recordId) and (RechargeRecords.status eq "pending")
        }) {
            it[RechargeRecords.status] = "success"
            it[RechargeRecords.paidAt] = paidAt
            if (transactionId != null) it[RechargeRecords.wechatOrderId] = transactionId
        }
        if (flipped == 0) return false

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
        awardRechargePoints(userId, amount, recordId)
        return true
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
     * 纯计算本次充值应得积分（不查库、不发放）。
     * 与 [awardRechargePoints] 使用同一规则读取逻辑，mock 同步入账时响应体需要积分值，
     * 发放已在入账事务内完成，这里只重放计算结果。
     */
    private suspend fun calculateRechargePoints(amount: BigDecimal): Int {
        val rule = PointsRules.selectAll()
            .where { (PointsRules.actionType eq "recharge") and (PointsRules.status eq 1) }
            .orderBy(PointsRules.id, SortOrder.ASC)
            .toList()
            .firstOrNull()
            ?: return 0
        return calculateRulePoints(
            amount,
            rule[PointsRules.pointsPerUnit],
            rule[PointsRules.unitAmount]
        )
    }

    /**
     * Update user wallet balance with optimistic locking.
     *
     * 返回变动前后的四个金额，供调用方写资金流水。
     *
     * 本方法运行在入账事务内，失败必须抛异常触发回滚：
     * 若改为返回错误结果，事务会正常提交，留下「充值记录已 success
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
            // 微信回调场景则返回 FAIL 触发稍后重试，而非留下资金不一致
            throw ConflictException("余额正在被其他操作变更，请稍后重试")
        }

        return WalletChange(balanceBefore, balanceAfter, bonusBefore, bonusAfter)
    }

    /** 生成商户单号：RC + 14 位时间戳 + 6 位随机数（微信要求 6~32 位字母数字）。 */
    private fun generateOutTradeNo(): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val rand = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
        return "RC$ts$rand"
    }

    private data class PendingOrderContext(
        val recordId: Long,
        val amount: BigDecimal,
        val bonusAmount: BigDecimal,
        val outTradeNo: String,
        val openid: String?
    )
}

/** 钱包一次变动的四个金额快照，仅用于组装资金流水。 */
private data class WalletChange(
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val bonusBefore: BigDecimal,
    val bonusAfter: BigDecimal
)

sealed class RechargeResult {
    /** 真实支付模式：订单已创建，附带小程序 wx.requestPayment 所需参数。 */
    data class Pending(
        val recordId: Long,
        val amount: Double,
        val bonusAmount: Double,
        val outTradeNo: String,
        val payParams: JsapiPayParams
    ) : RechargeResult()

    /** mock 模式（未配置支付）：下单即成功，资金/积分已入账。 */
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

/** 订单状态查询结果（GET /recharge/{id}/status）。 */
data class RechargeStatusInfo(
    val recordId: Long,
    /** pending/success/failed/refunded */
    val status: String,
    val amount: Double,
    val bonusAmount: Double,
    /** 仅 success 时有值 */
    val totalAdded: Double?,
    /** 保留字段：入账后由钱包/积分接口查询具体额度 */
    val pointsEarned: Int?,
    val paidAt: String?
)
