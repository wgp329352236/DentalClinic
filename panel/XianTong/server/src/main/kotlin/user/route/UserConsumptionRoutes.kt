package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.ConsumptionRecordService
import com.dentalclinic.user.service.UserConsumptionPaymentService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * User consumption record routes (protected by JWT).
 */
fun Application.userConsumptionRoutes(
    consumptionService: ConsumptionRecordService,
    paymentService: UserConsumptionPaymentService
) {
    routing {
        route("/api/v1/consumption") {
            userAuthenticated {
            /**
             * Get user's consumption records.
             * GET /api/v1/consumption/records?page=1&pageSize=20&startDate=2026-01-01&endDate=2026-12-31&clinicId=1
             */
            get("/records") {
                val userId = call.userId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                
                // Optional filters
                val startDate = call.request.queryParameters["startDate"]
                val endDate = call.request.queryParameters["endDate"]
                val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()

                val response = consumptionService.getConsumptionRecords(
                    userId = userId,
                    page = page,
                    pageSize = pageSize,
                    startDate = startDate,
                    endDate = endDate,
                    clinicId = clinicId
                )
                
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
             * Get consumption statistics.
             * GET /api/v1/consumption/statistics
             */
            get("/statistics") {
                val userId = call.userId()
                val statistics = consumptionService.getConsumptionStatistics(userId)
                call.respond(ApiResponse(code = 0, message = "success", data = statistics))
            }

            /**
             * Get unpaid consumptions (需求 P0-3：余额不足待微信支付).
             * GET /api/v1/consumption/unpaid
             */
            get("/unpaid") {
                val userId = call.userId()
                val unpaid = paymentService.getUnpaidConsumptions(userId)
                call.respond(ApiResponse(code = 0, message = "success", data = unpaid))
            }

            /**
             * Initiate payment for an unpaid consumption (需求 P0-3).
             * POST /api/v1/consumption/pay/{id}
             * Body: { "openid": "xxx" }
             */
            post("/pay/{id}") {
                val userId = call.userId()
                val consumptionId = call.parameters["id"]?.toLongOrNull()
                if (consumptionId == null) {
                    call.respond(ApiResponse<Any>(code = 400, message = "无效的消费ID"))
                    return@post
                }
                
                val request = call.receive<PaymentRequest>()
                val payParams = paymentService.initiatePayment(userId, consumptionId, request.openid)
                call.respond(ApiResponse(code = 0, message = "success", data = payParams))
            }
            }
        }
    }
}

@Serializable
data class PaymentRequest(val openid: String)
