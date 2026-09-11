package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.RechargeResult
import com.dentalclinic.user.service.RechargeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val logger = LoggerFactory.getLogger("UserRechargeRoutes")

/**
 * User recharge routes (protected by JWT).
 */
fun Application.userRechargeRoutes(rechargeService: RechargeService) {
    routing {
        route("/api/v1/recharge") {
            /**
             * Create a recharge order (simulated payment).
             * POST /api/v1/recharge/create
             * Header: Authorization: Bearer <access_token>
             * Body: { "amount": 1000, "clinicId": 1 }
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
                        is RechargeResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "充值成功",
                                data = RechargeResponseDto(
                                    recordId = result.recordId,
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
             * Get recharge tier rules.
             * GET /api/v1/recharge/tiers
             */
            get("/tiers") {
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = listOf(
                        TierDto(amount = 1000, bonus = 100, rate = "10%"),
                        TierDto(amount = 3000, bonus = 450, rate = "15%"),
                        TierDto(amount = 5000, bonus = 1000, rate = "20%"),
                        TierDto(amount = 10000, bonus = 2500, rate = "25%")
                    )
                ))
            }
        }
    }
}

@Serializable
data class RechargeRequest(val amount: Double, val clinicId: Long)

@Serializable
data class RechargeResponseDto(
    val recordId: Long,
    val amount: Double,
    val bonusAmount: Double,
    val totalAdded: Double,
    /** 本次充值获得的积分（需求 3.4.1：每 1 元积 0.5 分，仅计实际支付金额） */
    val pointsEarned: Int,
    val paidAt: String
)

@Serializable
data class TierDto(val amount: Int, val bonus: Int, val rate: String)
