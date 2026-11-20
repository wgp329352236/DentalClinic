package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.service.ConsumptionAdminService
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.wx.WechatConfig
import com.dentalclinic.common.wx.WechatPayClient
import com.dentalclinic.common.wx.WechatPayException
import com.dentalclinic.common.wx.WxPayNotification
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 用户端消费尾款支付服务。
 *
 * 管理端录入消费时若余额不足，记录以 paymentStatus=pending 落库，但积分、等级、
 * 推荐奖励、预约完成等权益必须等微信尾款支付成功后才结算。异步回调与前端主动查单
 * 最终都调用 [ConsumptionAdminService.finalizePaidConsumption]，由条件更新保证只结算一次。
 */
class UserConsumptionPaymentService(
    private val settlementService: ConsumptionAdminService
) : BaseService() {

    private val logger = LoggerFactory.getLogger("UserConsumptionPaymentService")

    /** 查询用户待支付的消费记录。 */
    suspend fun getUnpaidConsumptions(userId: Long): List<UnpaidConsumptionDto> = dbQuery {
        ConsumptionRecords.selectAll()
            .where {
                (ConsumptionRecords.userId eq userId) and
                    (ConsumptionRecords.paymentStatus eq "pending")
            }
            .orderBy(ConsumptionRecords.createdAt, SortOrder.DESC)
            .toList()
            .map { row ->
                UnpaidConsumptionDto(
                    id = row[ConsumptionRecords.id],
                    amount = row[ConsumptionRecords.amount].toDouble(),
                    wechatPaid = row[ConsumptionRecords.wechatPaid].toDouble(),
                    description = row[ConsumptionRecords.description],
                    createdAt = row[ConsumptionRecords.createdAt].format(DATE_TIME_FORMATTER)
                )
            }
    }

    /**
     * 发起待支付消费的微信支付。openid 必须取当前登录用户的服务端记录，不能信任客户端传参。
     */
    suspend fun initiatePayment(userId: Long, consumptionId: Long): ConsumptionPaymentInitDto {
        val context = dbQuery {
            val record = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.id eq consumptionId) and
                        (ConsumptionRecords.userId eq userId) and
                        (ConsumptionRecords.paymentStatus eq "pending")
                }
                .toList()
                .singleOrNull()
                ?: throw NotFoundException("消费记录不存在或已支付")

            val amount = record[ConsumptionRecords.wechatPaid]
            if (amount <= BigDecimal.ZERO) throw NotFoundException("该消费无需微信支付")

            val openid = Users.selectAll()
                .where { Users.id eq userId }
                .toList()
                .singleOrNull()
                ?.get(Users.openid)
                ?.takeIf { it.isNotBlank() }
                ?: throw NotFoundException("账户信息异常，请重新登录后再试")

            PaymentOrderContext(
                recordId = record[ConsumptionRecords.id],
                amount = amount,
                description = record[ConsumptionRecords.description],
                outTradeNo = record[ConsumptionRecords.outTradeNo],
                prepayId = record[ConsumptionRecords.prepayId],
                openid = openid
            )
        }

        // 本地未配置真实微信支付时，仅做演示入账；生产预检会阻断这种配置。
        if (!WechatConfig.pay.enabled) {
            val finalized = settlementService.finalizePaidConsumption(
                consumptionId = context.recordId,
                wechatTransactionId = "MOCK_CONSUMPTION_${context.recordId}"
            )
            if (!finalized) throw NotFoundException("消费记录不存在或已支付")
            return ConsumptionPaymentInitDto(
                consumptionId = context.recordId,
                status = "success",
                mock = true,
                amount = context.amount.toDouble(),
                payParams = null
            )
        }

        // 已创建过预支付单则复用 prepay_id，重新生成带当前时间戳的支付签名，避免重复下单。
        val prepayId = context.prepayId?.takeIf { it.isNotBlank() } ?: run {
            val outTradeNo = context.outTradeNo?.takeIf { it.isNotBlank() } ?: generateOutTradeNo(context.recordId)
            val amountFen = context.amount
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
            val createdPrepayId = WechatPayClient.createJsapiOrder(
                outTradeNo = outTradeNo,
                amountFen = amountFen,
                description = context.description ?: "诊疗费支付",
                openid = context.openid
            )

            ConsumptionRecords.update({
                (ConsumptionRecords.id eq context.recordId) and
                    (ConsumptionRecords.paymentStatus eq "pending")
            }) {
                it[ConsumptionRecords.outTradeNo] = outTradeNo
                it[ConsumptionRecords.prepayId] = createdPrepayId
            }
            createdPrepayId
        }

        val payParams = WechatPayClient.buildJsapiPayParams(prepayId)
        return ConsumptionPaymentInitDto(
            consumptionId = context.recordId,
            status = "pending",
            mock = false,
            amount = context.amount.toDouble(),
            payParams = PaymentParamsDto(
                timeStamp = payParams.timeStamp,
                nonceStr = payParams.nonceStr,
                packageValue = payParams.packageValue,
                signType = payParams.signType,
                paySign = payParams.paySign
            )
        )
    }

    /**
     * 微信支付异步通知。金额/状态校验通过后调用幂等结算入口。
     */
    suspend fun handlePayNotification(notification: WxPayNotification): Boolean {
        val record = dbQuery {
            ConsumptionRecords.selectAll()
                .where { ConsumptionRecords.outTradeNo eq notification.outTradeNo }
                .toList()
                .singleOrNull()
        } ?: run {
            logger.error("[ConsumptionPayment] 找不到消费订单 out_trade_no=${notification.outTradeNo}")
            return false
        }

        val expectedFen = record[ConsumptionRecords.wechatPaid]
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
        if (notification.totalFen != expectedFen) {
            logger.error(
                "[ConsumptionPayment] 回调金额不符 out_trade_no=${notification.outTradeNo} " +
                    "期望=${expectedFen}分 回调=${notification.totalFen}分"
            )
            return false
        }

        return when (record[ConsumptionRecords.paymentStatus]) {
            "success" -> {
                logger.info("[ConsumptionPayment] 消费 {} 已结算，忽略重复回调", record[ConsumptionRecords.id])
                true
            }
            "pending" -> {
                if (notification.tradeState == "SUCCESS") {
                    settlementService.finalizePaidConsumption(
                        consumptionId = record[ConsumptionRecords.id],
                        wechatTransactionId = notification.transactionId
                    )
                } else {
                    // 非成功状态不结算；微信仍可后续推送 SUCCESS 通知。
                    logger.info(
                        "[ConsumptionPayment] 消费 {} 支付状态 {}，暂不结算",
                        record[ConsumptionRecords.id], notification.tradeState
                    )
                    true
                }
            }
            else -> true
        }
    }

    /**
     * 查询支付状态。pending 时主动向微信查单，避免异步通知延迟导致前端长时间等待。
     */
    suspend fun getPaymentStatus(userId: Long, consumptionId: Long): ConsumptionPaymentStatusDto? {
        var record = dbQuery {
            ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.id eq consumptionId) and
                        (ConsumptionRecords.userId eq userId)
                }
                .toList()
                .singleOrNull()
        } ?: return null

        if (record[ConsumptionRecords.paymentStatus] == "pending" && WechatConfig.pay.enabled) {
            val outTradeNo = record[ConsumptionRecords.outTradeNo]
            if (!outTradeNo.isNullOrBlank()) {
                val state = try {
                    WechatPayClient.queryOrderByOutTradeNo(outTradeNo)
                } catch (e: WechatPayException) {
                    logger.warn("[ConsumptionPayment] 主动查单失败 $outTradeNo: ${e.message}")
                    null
                }

                when (state?.tradeState) {
                    "SUCCESS" -> settlementService.finalizePaidConsumption(
                        consumptionId = record[ConsumptionRecords.id],
                        wechatTransactionId = state.transactionId
                    )
                    // 关单/支付失败后清理旧商户单，用户再次点击支付时会生成新单。
                    "CLOSED", "REVOKED", "PAYERROR" -> dbQuery {
                        ConsumptionRecords.update({
                            (ConsumptionRecords.id eq consumptionId) and
                                (ConsumptionRecords.paymentStatus eq "pending")
                        }) {
                            it[ConsumptionRecords.outTradeNo] = null
                            it[ConsumptionRecords.prepayId] = null
                        }
                    }
                }
            }
        }

        record = dbQuery {
            ConsumptionRecords.selectAll()
                .where { ConsumptionRecords.id eq consumptionId }
                .toList()
                .singleOrNull()
        } ?: return null

        return ConsumptionPaymentStatusDto(
            consumptionId = consumptionId,
            status = record[ConsumptionRecords.paymentStatus],
            amount = record[ConsumptionRecords.amount].toDouble(),
            wechatPaid = record[ConsumptionRecords.wechatPaid].toDouble(),
            pointsEarned = record[ConsumptionRecords.pointsEarned],
            paidAt = record[ConsumptionRecords.paidAt]?.format(DATE_TIME_FORMATTER)
        )
    }

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val CONSUMPTION_TRADE_PREFIX = "CS"

        /** 消费支付单号前缀，供统一支付回调分流；兼容早期未上线的 CONSUMPTION_ 前缀。 */
        fun isConsumptionOutTradeNo(outTradeNo: String): Boolean =
            outTradeNo.startsWith(CONSUMPTION_TRADE_PREFIX) || outTradeNo.startsWith("CONSUMPTION_")

        /**
         * 生成 6~32 位商户单号，避免旧实现 UUID 拼接后超过 out_trade_no VARCHAR(64) 的长度。
         */
        private fun generateOutTradeNo(consumptionId: Long): String {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val rand = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
            val shortId = consumptionId.toString().takeLast(8)
            return "$CONSUMPTION_TRADE_PREFIX$ts$rand$shortId"
        }

        private data class PaymentOrderContext(
            val recordId: Long,
            val amount: BigDecimal,
            val description: String?,
            val outTradeNo: String?,
            val prepayId: String?,
            val openid: String
        )
    }
}

@Serializable
data class UnpaidConsumptionDto(
    val id: Long,
    val amount: Double,
    val wechatPaid: Double,
    val description: String?,
    val createdAt: String
)

@Serializable
data class ConsumptionPaymentInitDto(
    val consumptionId: Long,
    /** pending=需调起微信支付；success=本地 mock 已完成支付结算 */
    val status: String,
    val mock: Boolean,
    val amount: Double,
    val payParams: PaymentParamsDto? = null
)

@Serializable
data class PaymentParamsDto(
    val timeStamp: String,
    val nonceStr: String,
    @SerialName("package") val packageValue: String,
    val signType: String,
    val paySign: String
)

@Serializable
data class ConsumptionPaymentStatusDto(
    val consumptionId: Long,
    val status: String,
    val amount: Double,
    val wechatPaid: Double,
    val pointsEarned: Int,
    val paidAt: String? = null
)
