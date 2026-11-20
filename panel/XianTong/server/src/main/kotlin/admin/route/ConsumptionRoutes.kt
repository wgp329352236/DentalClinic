package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.ConsumptionAdminService
import com.dentalclinic.admin.service.CreateConsumptionRequest
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * 后台消费管理路由。
 *
 * 只负责 HTTP 协议处理（参数解析、权限校验、响应封装），结算逻辑全部在
 * [ConsumptionAdminService] 中，保证「折扣/券/扣款/积分/升级/奖励/通知」在单一事务内完成。
 */
fun Route.consumptionRoutes(consumptionService: ConsumptionAdminService) {
    route("/consumptions") {
        authenticated {
            // POST /api/admin/consumptions - 录入消费并完成结算
            requirePermission("consumption:create") {
                post {
                    val request = call.receive<CreateConsumptionRequest>()
                    val principal = call.adminPrincipal()

                    if (request.userId <= 0) {
                        throw ValidationException("无效的客户ID")
                    }
                    if (request.clinicId <= 0) {
                        throw ValidationException("无效的门店ID")
                    }
                    if (request.originalAmount <= 0) {
                        throw ValidationException("消费金额必须大于0")
                    }
                    if (request.appointmentId != null && request.appointmentId <= 0) {
                        throw ValidationException("无效的预约ID")
                    }
                    if (request.userCouponId != null && request.userCouponId <= 0) {
                        throw ValidationException("无效的优惠券ID")
                    }
                    if (request.usePoints < 0) {
                        throw ValidationException("积分抵扣数量不能为负数")
                    }

                    val result = consumptionService.createConsumption(request, principal.username, call.clinicScope())
                    call.ok(result, "录入成功")
                }
            }

            // GET /api/admin/consumptions - 分页查询消费流水
            requirePermission("consumption:list") {
                get {
                    val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                    val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val startDate = call.request.queryParameters["startDate"]
                    val endDate = call.request.queryParameters["endDate"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                    val response = consumptionService.listConsumptions(
                        userId = userId,
                        clinicId = clinicId,
                        scope = call.clinicScope(),
                        startDate = startDate,
                        endDate = endDate,
                        page = page.coerceAtLeast(1),
                        pageSize = pageSize.coerceIn(1, 100)
                    )
                    call.ok(response)
                }
            }
        }
    }
}
