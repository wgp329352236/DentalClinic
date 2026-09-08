package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * User referral routes (protected by JWT).
 */
fun Application.userReferralRoutes() {
    val referralService = ReferralService()

    routing {
        route("/api/v1/referral") {
            userAuthenticated {
            /**
             * Get user's referral code.
             * GET /api/v1/referral/code
             */
            get("/code") {
                val userId = call.userId()
                val code = referralService.getReferralCode(userId)
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = mapOf("referralCode" to code, "shareLink" to "https://example.com/register?code=$code")
                ))
            }

            /**
             * Get referral records (people referred by user).
             * GET /api/v1/referral/records?page=1&pageSize=20
             */
            get("/records") {
                val userId = call.userId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = referralService.getReferralRecords(userId, page, pageSize)
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = PageResponse(
                        list = response.list,
                        total = response.total,
                        page = response.page,
                        pageSize = response.pageSize
                    )
                ))
            }

            /**
             * Register with referral code (called during user registration).
             * POST /api/v1/referral/register
             */
            post("/register") {
                val userId = call.userId()

                try {
                    val request = call.receive<RegisterWithReferralRequest>()
                    val result = referralService.registerWithReferral(userId, request.referralCode)

                    when (result) {
                        is ReferralResult.Success -> {
                            call.respond(ApiResponse<Unit>(code = 0, message = "推荐关系绑定成功"))
                        }
                        is ReferralResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(code = 500, message = "绑定失败: ${e.message}"))
                }
            }
            }
        }
    }
}

@Serializable
data class RegisterWithReferralRequest(val referralCode: String)
