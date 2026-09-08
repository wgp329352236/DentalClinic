package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.PointsResult
import com.dentalclinic.user.service.PointsService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * User points routes (protected by JWT).
 */
fun Application.userPointsRoutes() {
    val pointsService = PointsService()

    routing {
        route("/api/v1/points") {
            userAuthenticated {
            /**
             * Get points balance.
             * GET /api/v1/points/balance
             */
            get("/balance") {
                val userId = call.userId()
                val balance = pointsService.getPointsBalance(userId)
                call.respond(ApiResponse(code = 0, message = "success", data = balance))
            }

            /**
             * Get points records with pagination.
             * GET /api/v1/points/records?page=1&pageSize=20
             */
            get("/records") {
                val userId = call.userId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = pointsService.getPointsRecords(userId, page, pageSize)
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
             * Daily check-in.
             * POST /api/v1/points/checkin
             */
            post("/checkin") {
                val userId = call.userId()
                val result = pointsService.checkIn(userId)
                when (result) {
                    is PointsResult.Success -> {
                        call.respond(ApiResponse(
                            code = 0,
                            message = "签到成功",
                            data = mapOf("pointsEarned" to result.pointsEarned)
                        ))
                    }
                    is PointsResult.Error -> {
                        call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                }
            }

            /**
             * Complete profile reward.
             * POST /api/v1/points/profile-complete
             */
            post("/profile-complete") {
                val userId = call.userId()
                val result = pointsService.completeProfileReward(userId)
                when (result) {
                    is PointsResult.Success -> {
                        call.respond(ApiResponse(
                            code = 0,
                            message = "奖励发放成功",
                            data = mapOf("pointsEarned" to result.pointsEarned)
                        ))
                    }
                    is PointsResult.Error -> {
                        call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                }
            }
            }
        }
    }
}
