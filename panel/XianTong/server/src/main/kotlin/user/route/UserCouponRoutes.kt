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
                                data = mapOf(
                                    "couponName" to result.couponName,
                                    "faceValue" to result.faceValue
                                )
                            ))
                        }
                        is CouponRedeemResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(code = 500, message = "核销失败: ${e.message}"))
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
                                data = mapOf(
                                    "couponCode" to result.couponCode,
                                    "couponName" to result.couponName,
                                    "faceValue" to result.faceValue,
                                    "validTo" to result.validTo
                                )
                            ))
                        }
                        is CouponClaimResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(code = 500, message = "领取失败: ${e.message}"))
                }
            }
            }
        }
    }
}

@Serializable
data class RedeemCouponRequest(val couponCode: String, val appointmentId: Long?)

