package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.common.RechargeTierService
import com.dentalclinic.common.wx.WechatPayClient
import com.dentalclinic.common.wx.WechatPayException
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.RechargeResult
import com.dentalclinic.user.service.RechargeService
import com.dentalclinic.user.service.UserConsumptionPaymentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val logger = LoggerFactory.getLogger("UserRechargeRoutes")

/**
 * User recharge routes.
 *
 * - POST /create、GET /{id}/status 需登录（与本项目其他 C 端接口一致，路由内手动解 Bearer）；
 * - POST /notify 是微信支付服务器回调，必须公开：真实性由平台证书验签 + AEAD 解密保证，
 *   绝不能要求用户 JWT，否则微信侧永远收得到 401 而无法投递支付结果。
 */
fun Application.userRechargeRoutes(
    rechargeService: RechargeService,
    consumptionPaymentService: UserConsumptionPaymentService
) {
    routing {
        route("/api/v1/recharge") {
            /**
             * Create a recharge order.
             * POST /api/v1/recharge/create
             * Header: Authorization: Bearer <access_token>
             * Body: { "amount": 1000, "clinicId": 1 }
             *
             * 返回 status=pending 时 data 带 payParams，小程序需继续调 wx.requestPayment；
             * 返回 status=success（未配置支付的 mock 模式）时资金已到账。
             */
            post("/create") {
                // Verify authentication
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@post
                }

                val token = authHeader.removePrefix("Bearer ")
                val decoded = UserJwtConfig.verifyAccessToken(token)
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@post
                }

                val userId = decoded.subject.toLong()

                try {
                    val request = call.receive<RechargeRequest>()
                    val result = rechargeService.createRechargeOrder(
                        userId = userId,
                        clinicId = request.clinicId,
                        amount = BigDecimal(request.amount.toString())
                    )

                    when (result) {
                        is RechargeResult.Pending -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "订单已创建，请完成支付",
                                data = RechargeResponseDto(
                                    recordId = result.recordId,
                                    status = "pending",
                                    mock = false,
                                    amount = result.amount,
                                    bonusAmount = result.bonusAmount,
                                    payParams = PayParamsDto(
                                        timeStamp = result.payParams.timeStamp,
                                        nonceStr = result.payParams.nonceStr,
                                        packageValue = result.payParams.packageValue,
                                        signType = result.payParams.signType,
                                        paySign = result.payParams.paySign
                                    )
                                )
                            ))
                        }
                        is RechargeResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "充值成功",
                                data = RechargeResponseDto(
                                    recordId = result.recordId,
                                    status = "success",
                                    mock = true,
                                    amount = result.amount,
                                    bonusAmount = result.bonusAmount,
                                    totalAdded = result.totalAdded,
                                    pointsEarned = result.pointsEarned,
                                    paidAt = result.paidAt
                                )
                            ))
                        }
                        is RechargeResult.Error -> {
                            call.respond(ApiResponse<Unit>(
                                code = 400,
                                message = result.message
                            ))
                        }
                    }
                } catch (e: ConflictException) {
                    // 乐观锁冲突属可重试的并发问题，回 409 让前端提示稍后再试，而不是当成服务器故障
                    logger.warn("[Recharge]: 用户 $userId 充值遇并发冲突: ${e.message}")
                    call.respond(ApiResponse<Unit>(
                        code = 409,
                        message = e.message ?: "余额正在被其他操作变更，请稍后重试"
                    ))
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Recharge]: 用户 $userId 充值", "充值失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 微信支付结果异步通知（公开端点，充值与消费尾款共用）。
             * POST /api/v1/recharge/notify
             *
             * 微信约定：HTTP 200 且 body {"code":"SUCCESS"} 才算投递成功，否则按策略重试多次。
             * 验签/解密失败回 401；业务校验失败（订单不存在、金额不符）或入账异常回 500，
             * 让微信重试以争取最终一致；重复通知幂等 ACK。
             */
            post("/notify") {
                // 必须取原始报文文本参与验签：先反序列化再重拼会改变字节，签名必失败
                val rawBody = call.receiveText()
                val notification = try {
                    WechatPayClient.verifyAndDecryptNotification(rawBody, call.request.headers)
                } catch (e: WechatPayException) {
                    logger.warn("[Recharge]: 支付回调验签/解密被拒: ${e.message}")
                    call.respondText(
                        NOTIFY_FAIL_VERIFY, ContentType.Application.Json, HttpStatusCode.Unauthorized
                    )
                    return@post
                } catch (e: Exception) {
                    logger.error("[Recharge]: 支付回调解析异常", e)
                    call.respondText(
                        NOTIFY_FAIL_BAD_REQUEST, ContentType.Application.Json, HttpStatusCode.BadRequest
                    )
                    return@post
                }

                // 充值单以 RC 开头，消费尾款单以 CS 开头；同一个微信 notify_url 统一接收。
                val accepted = if (UserConsumptionPaymentService.isConsumptionOutTradeNo(notification.outTradeNo)) {
                    consumptionPaymentService.handlePayNotification(notification)
                } else {
                    rechargeService.handlePayNotification(notification)
                }
                if (accepted) {
                    call.respondText(NOTIFY_SUCCESS, ContentType.Application.Json)
                } else {
                    call.respondText(
                        NOTIFY_FAIL_BUSINESS, ContentType.Application.Json,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            /**
             * 查询充值订单状态（小程序调起支付后轮询）。
             * GET /api/v1/recharge/{id}/status
             */
            get("/{id}/status") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@get
                }
                val decoded = UserJwtConfig.verifyAccessToken(
                    authHeader.removePrefix("Bearer ")
                )
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@get
                }
                val userId = decoded.subject.toLong()
                val recordId = call.parameters["id"]?.toLongOrNull()
                if (recordId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "订单ID不合法"))
                    return@get
                }

                try {
                    val status = rechargeService.getRechargeStatus(recordId, userId)
                    if (status == null) {
                        call.respond(ApiResponse<Unit>(code = 404, message = "充值订单不存在"))
                        return@get
                    }
                    call.respond(
                        ApiResponse(
                            code = 0,
                            message = "success",
                            data = RechargeStatusDto(
                                recordId = status.recordId,
                                status = status.status,
                                amount = status.amount,
                                bonusAmount = status.bonusAmount,
                                totalAdded = status.totalAdded,
                                pointsEarned = status.pointsEarned,
                                paidAt = status.paidAt
                            )
                        )
                    )
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Recharge]: 用户 $userId 查询订单 $recordId 状态", "查询失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * Get recharge tier rules.
             * GET /api/v1/recharge/tiers
             */
            get("/tiers") {
                // 档位读后台 recharge_tier 配置（表空/异常时服务内部回退内置 4 档，结构不变）
                val tiers = try {
                    rechargeService.listTiers()
                } catch (e: Exception) {
                    logger.warn("[Recharge]: 读取充值档位失败: ${e.message}")
                    RechargeTierService.FALLBACK_TIERS
                }
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = tiers.map { tier ->
                        TierDto(
                            amount = tier.minAmount.toDouble(),
                            bonus = RechargeTierService.bonusOf(tier.minAmount, tier.bonusRate).toDouble(),
                            rate = RechargeTierService.rateLabel(tier.bonusRate)
                        )
                    }
                ))
            }
        }
    }
}

/** 充值下单请求；clinicId 可省略，由后端按最近到店/默认营业门店解析。 */
@Serializable
data class RechargeRequest(val amount: Double, val clinicId: Long = 0)

/**
 * 创建充值订单响应。
 *
 * @param status pending=待调起支付（payParams 有值）；success=已到账（mock 模式）
 * @param mock true 表示后端未配置真实微信支付，本次为模拟入账，仅会出现在本地/演示环境
 */
@Serializable
data class RechargeResponseDto(
    val recordId: Long,
    val status: String,
    val mock: Boolean = false,
    val amount: Double,
    val bonusAmount: Double,
    val totalAdded: Double? = null,
    val pointsEarned: Int? = null,
    val paidAt: String? = null,
    val payParams: PayParamsDto? = null
)

/** 小程序 wx.requestPayment 入参（字段名与微信 API 保持一致）。 */
@Serializable
data class PayParamsDto(
    val timeStamp: String,
    val nonceStr: String,
    @SerialName("package") val packageValue: String,
    val signType: String,
    val paySign: String
)

@Serializable
data class RechargeStatusDto(
    val recordId: Long,
    /** pending/success/failed/refunded */
    val status: String,
    val amount: Double,
    val bonusAmount: Double,
    val totalAdded: Double? = null,
    val pointsEarned: Int? = null,
    val paidAt: String? = null
)

/** 充值档位展示项；金额用 Double 承载 decimal(12,2)，小程序按 number 消费。 */
@Serializable
data class TierDto(val amount: Double, val bonus: Double, val rate: String)

// 微信支付回调应答为微信约定的固定 JSON 文本，直接写字面量避免序列化器差异
private const val NOTIFY_SUCCESS = """{"code":"SUCCESS"}"""
private const val NOTIFY_FAIL_VERIFY = """{"code":"FAIL","message":"签名验证失败"}"""
private const val NOTIFY_FAIL_BAD_REQUEST = """{"code":"FAIL","message":"回调报文异常"}"""
private const val NOTIFY_FAIL_BUSINESS = """{"code":"FAIL","message":"入账校验失败"}"""
