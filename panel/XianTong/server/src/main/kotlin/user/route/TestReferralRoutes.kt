package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Test routes for referral first consumption reward.
 * In production, this would be triggered by payment callback.
 */
fun Application.testReferralRoutes() {
    val referralService = ReferralService()

    routing {
        route("/api/v1/test") {
            /**
             * Simulate first consumption for a referred user.
             * POST /api/v1/test/referral-first-consumption?refereeId=12
             */
            post("/referral-first-consumption") {
                val userId = authenticate(call) ?: return@post
                val refereeIdParam = call.request.queryParameters["refereeId"]?.toLongOrNull()
                val targetRefereeId = refereeIdParam ?: userId

                val result = referralService.awardFirstConsumptionReward(targetRefereeId)
                when (result) {
                    is ReferralResult.Success -> {
                        call.respond(ApiResponse<Unit>(code = 0, message = "首消奖励发放成功"))
                    }
                    is ReferralResult.Error -> {
                        call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                }
            }
        }
    }
}

private suspend fun authenticate(call: io.ktor.server.application.ApplicationCall): Long? {
    val authHeader = call.request.headers[HttpHeaders.Authorization]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
        return null
    }

    val token = authHeader.removePrefix("Bearer ")
    val decoded = UserJwtConfig.verifyAccessToken(token)
    if (decoded == null) {
        call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
        return null
    }

    return decoded.subject.toLong()
}
