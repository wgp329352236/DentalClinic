package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.PointsMallResult
import com.dentalclinic.user.service.PointsMallService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserPointsMallRoutes")

/**
 * 积分商城路由（需求 3.4.2）。
 *
 * - GET  /api/v1/points/mall/goods   上架商品列表
 * - POST /api/v1/points/mall/exchange 兑换（扣积分 + 生成核销码）
 * - GET  /api/v1/points/mall/orders  我的兑换订单
 */
fun Application.userPointsMallRoutes(pointsMallService: PointsMallService) {
    routing {
        route("/api/v1/points/mall") {
            userAuthenticated {

                get("/goods") {
                    val goods = pointsMallService.listGoods()
                    call.respond(ApiResponse(code = 0, message = "success", data = goods))
                }

                post("/exchange") {
                    val userId = call.userId()
                    try {
                        val request = call.receive<ExchangeGoodsRequest>()
                        when (val result = pointsMallService.exchange(userId, request.goodsId)) {
                            is PointsMallResult.Success -> {
                                call.respond(
                                    ApiResponse(code = 0, message = "兑换成功，请凭核销码到店使用", data = result.order)
                                )
                            }
                            is PointsMallResult.Error -> {
                                call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                            }
                        }
                    } catch (e: Exception) {
                        val (code, message) = translateApiError(
                            logger, "[PointsMall]: 用户 $userId 积分兑换", "兑换失败，请稍后重试", e
                        )
                        call.respond(ApiResponse<Unit>(code = code, message = message))
                    }
                }

                get("/orders") {
                    val userId = call.userId()
                    val orders = pointsMallService.getMyOrders(userId)
                    call.respond(ApiResponse(code = 0, message = "success", data = orders))
                }
            }
        }
    }
}

@Serializable
data class ExchangeGoodsRequest(val goodsId: Long)
