package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserCouponRoutes")

/**
 * User coupon routes (protected by JWT).
 */
fun Application.userCouponRoutes(couponService: CouponService) {
    routing {
        route("/api/v1/coupons") {
            userAuthenticated {
            /**
             * Get user's coupon list.
             * GET /api/v1/coupons?status=unused
             */
            get("") {
                val userId = call.userId()
                val status = call.request.queryParameters["status"]
                val coupons = couponService.getUserCoupons(userId, status)
                call.respond(ApiResponse(code = 0, message = "success", data = coupons))
            }

            /**
             * Get coupon detail for QR code generation.
             * GET /api/v1/coupons/{id}/detail
             */
            get("/{id}/detail") {
                val userId = call.userId()
                val couponId = call.parameters["id"]?.toLongOrNull()

                if (couponId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的优惠券ID"))
                    return@get
                }

                val detail = couponService.getCouponDetail(userId, couponId)
                if (detail != null) {
                    call.respond(ApiResponse(code = 0, message = "success", data = detail))
                } else {
                    call.respond(ApiResponse<Unit>(code = 404, message = "优惠券不存在"))
                }
            }

            /**
             * Redeem a coupon.
             * POST /api/v1/coupons/redeem
             * Body: { "couponCode": "NEW20057523", "appointmentId": 1 }
             */
            post("/redeem") {
                val userId = call.userId()

                try {
                    val request = call.receive<RedeemCouponRequest>()
                    val result = couponService.redeemCoupon(userId, request.couponCode, request.appointmentId)

                    when (result) {
                        is CouponRedeemResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "核销成功",
                                data = CouponRedeemDto(result.couponName, result.faceValue)
                            ))
                        }
                        is CouponRedeemResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    // 业务错误已由 CouponRedeemResult.Error 走 400 分支，落到这里的只能是底层异常
                    val (code, message) = translateApiError(
                        logger, "[Coupon]: 用户 $userId 核销", "核销失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 推广领券：扫码进入小程序自动领取 2 张券（幂等，每人每模板限领一次）。
             * POST /api/v1/coupons/promo-claim
             * Body: { "templateId": 1 }
             */
            post("/promo-claim") {
                val userId = call.userId()

                try {
                    val request = call.receive<PromoClaimRequest>()
                    if (request.templateId == null) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "缺少模板ID"))
                        return@post
                    }
                    val result = couponService.claimPromo(userId, request.templateId)

                    when (result) {
                        is PromoClaimResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "领取成功",
                                data = PromoClaimDto(result.coupons)
                            ))
                        }
                        is PromoClaimResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger,
                        "[Coupon]: 用户 $userId 推广领券",
                        "领取失败，请稍后重试",
                        e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * Claim a coupon by template ID.
             * POST /api/v1/coupons/claim/{templateId}
             */
            post("/claim/{templateId}") {
                val userId = call.userId()
                val templateId = call.parameters["templateId"]?.toLongOrNull()

                if (templateId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的模板ID"))
                    return@post
                }

                try {
                    val result = couponService.claimCoupon(userId, templateId)

                    when (result) {
                        is CouponClaimResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "领取成功",
                                data = CouponClaimDto(
                                    couponCode = result.couponCode,
                                    couponName = result.couponName,
                                    faceValue = result.faceValue,
                                    validTo = result.validTo
                                )
                            ))
                        }
                        is CouponClaimResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger,
                        "[Coupon]: 用户 $userId 领取优惠券, templateId=$templateId",
                        "领取失败，请稍后重试",
                        e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
            }
        }
    }
}

@Serializable
data class RedeemCouponRequest(val couponCode: String, val appointmentId: Long?)

@Serializable
data class PromoClaimRequest(val templateId: Long? = null)

// 成功响应用具名 DTO：mapOf 会推断为 Map<String, Any>，kotlinx 无法序列化
@Serializable
data class CouponRedeemDto(val couponName: String, val faceValue: Double)

@Serializable
data class CouponClaimDto(
    val couponCode: String,
    val couponName: String,
    val faceValue: Double,
    val validTo: String
)

@Serializable
data class PromoClaimDto(val coupons: List<PromoClaimedCoupon>)

