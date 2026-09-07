package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * User coupon routes (protected by JWT).
 */
fun Application.userCouponRoutes() {
    val couponService = CouponService()

    routing {
        route("/api/v1/coupons") {
            /**
             * Get user's coupon list.
             * GET /api/v1/coupons?status=unused
             */
            get("") {
                val userId = authenticate(call) ?: return@get
                val status = call.request.queryParameters["status"]
                val coupons = couponService.getUserCoupons(userId, status)
                call.respond(ApiResponse(code = 0, message = "success", data = coupons))
            }

            /**
             * Redeem a coupon.
             * POST /api/v1/coupons/redeem
             * Body: { "couponCode": "NEW20057523", "appointmentId": 1 }
             */
            post("/redeem") {
                val userId = authenticate(call) ?: return@post

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
        }
    }
}

@Serializable
data class RedeemCouponRequest(val couponCode: String, val appointmentId: Long?)

