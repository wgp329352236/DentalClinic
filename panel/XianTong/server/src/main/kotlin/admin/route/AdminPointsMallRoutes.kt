package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.AdminPrincipalKey
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.service.AdminPointsMallService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * 管理端积分商城路由（P1-9：核销台）。
 */
fun Route.adminPointsMallRoutes(adminPointsMallService: AdminPointsMallService) {
    route("/points-mall") {
        // 查询待核销订单列表
        requirePermission("points_mall:redeem") {
            get("/pending-orders") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = adminPointsMallService.listPendingOrders(page, pageSize)
                call.respond(ApiResponse.ok(response))
            }
        }

        // 核销兑换订单
        requirePermission("points_mall:redeem") {
            post("/redeem") {
                val request = call.receive<RedeemRequest>()
                val principal = call.attributes[AdminPrincipalKey]
                val operatorName = principal.username

                val result = adminPointsMallService.redeemOrder(request.redeemCode, operatorName)
                call.respond(ApiResponse.ok(result, "核销成功"))
            }
        }
    }
}

@Serializable
data class RedeemRequest(val redeemCode: String)
