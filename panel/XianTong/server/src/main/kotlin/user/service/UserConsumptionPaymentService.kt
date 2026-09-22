package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.wx.WechatPayClient
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 用户端消费支付服务（需求 P0-3：余额不足时微信支付补齐）。
 *
 * 当管理端录入消费且余额不足时，消费记录标记为 paymentStatus='pending'。
 * 用户可通过此服务查询待支付消费并发起真实微信支付。
 */
class UserConsumptionPaymentService : BaseService() {

    private val logger = LoggerFactory.getLogger("UserConsumptionPaymentService")

    /**
     * 查询用户待支付的消费记录。
     */
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
     * 发起待支付消费的微信支付。
     *
     * 调用 WechatPayClient 创建支付订单，返回前端调起支付所需的参数。
     * 支付成功后由回调接口标记为已支付。
     */
    suspend fun initiatePayment(userId: Long, consumptionId: Long, openid: String): PaymentParamsDto = dbQuery {
        val record = ConsumptionRecords.selectAll()
            .where {
                (ConsumptionRecords.id eq consumptionId) and
                    (ConsumptionRecords.userId eq userId) and
                    (ConsumptionRecords.paymentStatus eq "pending")
            }
            .toList()
            .firstOrNull()
            ?: throw IllegalArgumentException("消费记录不存在或已支付")

        val amount = record[ConsumptionRecords.wechatPaid]
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("该消费无需微信支付")
        }

        // 生成商户订单号（幂等：如果已有 outTradeNo 则复用）
        val existingOutTradeNo = record[ConsumptionRecords.outTradeNo]
        val outTradeNo = existingOutTradeNo ?: "CONSUMPTION_${consumptionId}_${UUID.randomUUID()}"

        // 调用微信支付创建订单
        val amountFen = (amount.multiply(BigDecimal(100))).toLong()
        val prepayId = WechatPayClient.createJsapiOrder(
            outTradeNo = outTradeNo,
            amountFen = amountFen,
            description = record[ConsumptionRecords.description] ?: "诊疗费支付",
            openid = openid
        )

        // 生成前端调起支付所需参数
        val payParams = WechatPayClient.buildJsapiPayParams(prepayId)

        // 存储 outTradeNo 和 prepayId（如果首次创建）
        if (existingOutTradeNo == null) {
            ConsumptionRecords.update({ ConsumptionRecords.id eq consumptionId }) {
                it[ConsumptionRecords.outTradeNo] = outTradeNo
                it[ConsumptionRecords.prepayId] = prepayId
            }
        }

        PaymentParamsDto(
            timeStamp = payParams.timeStamp,
            nonceStr = payParams.nonceStr,
            packageValue = payParams.packageValue,
            signType = payParams.signType,
            paySign = payParams.paySign
        )
    }

    /**
     * 微信支付回调处理：标记消费为已支付。
     *
     * 由 WechatPayClient 的回调处理器调用，验证金额后更新 paymentStatus。
     */
    suspend fun handlePaymentCallback(outTradeNo: String, amountYuan: BigDecimal): Boolean = dbQuery {
        val record = ConsumptionRecords.selectAll()
            .where { ConsumptionRecords.outTradeNo eq outTradeNo }
            .toList()
            .firstOrNull()
            ?: return@dbQuery false

        // 校验金额
        val expectedAmount = record[ConsumptionRecords.wechatPaid]
        if (expectedAmount.compareTo(amountYuan) != 0) {
            logger.error("[ConsumptionPayment] 回调金额不匹配：期望 $expectedAmount，实际 $amountYuan")
            return@dbQuery false
        }

        // 标记为已支付
        val updated = ConsumptionRecords.update({ ConsumptionRecords.id eq record[ConsumptionRecords.id] }) {
            it[ConsumptionRecords.paymentStatus] = "success"
        }

        if (updated > 0) {
            logger.info("[ConsumptionPayment] 消费 ${record[ConsumptionRecords.id]} 已支付，金额 $amountYuan")
        }

        updated > 0
    }

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
data class PaymentParamsDto(
    val timeStamp: String,
    val nonceStr: String,
    val packageValue: String,
    val signType: String,
    val paySign: String
)
