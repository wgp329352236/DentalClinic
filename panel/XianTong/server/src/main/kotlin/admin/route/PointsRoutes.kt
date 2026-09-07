package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.service.AdminPointsService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Admin points management routes.
 */
fun Route.pointsRoutes(pointsService: AdminPointsService) {
    route("/points") {
        authenticated {
            // GET /api/admin/points/rules - Get all points rules
            requirePermission("points:view") {
                get("/rules") {
                    val rules = pointsService.getPointsRules()
                    call.ok(rules)
                }
            }

            // POST /api/admin/points/rules - Create a new points rule
            requirePermission("points:manage") {
                post("/rules") {
                    val request = call.receive<com.dentalclinic.admin.service.CreatePointsRuleRequest>()

                    // Validate input
                    if (request.ruleName.isBlank()) {
                        throw ValidationException("规则名称不能为空")
                    }
                    if (request.pointsPerUnit <= 0) {
                        throw ValidationException("积分数量必须大于0")
                    }

                    val id = pointsService.createPointsRule(request)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            // PUT /api/admin/points/rules/{id} - Update a points rule
            requirePermission("points:manage") {
                put("/rules/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的规则ID")

                    val request = call.receive<com.dentalclinic.admin.service.UpdatePointsRuleRequest>()

                    // Validate input
                    if (request.ruleName.isBlank()) {
                        throw ValidationException("规则名称不能为空")
                    }
                    if (request.pointsPerUnit <= 0) {
                        throw ValidationException("积分数量必须大于0")
                    }

                    pointsService.updatePointsRule(id, request)
                    call.ok<Unit>(message = "更新成功")
                }
            }

            // GET /api/admin/points/records - Get points records with filters
            requirePermission("points:view") {
                get("/records") {
                    val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                    val sourceType = call.request.queryParameters["sourceType"]
                    val startDate = call.request.queryParameters["startDate"]
                    val endDate = call.request.queryParameters["endDate"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                    val response = pointsService.getPointsRecords(
                        userId = userId,
                        sourceType = sourceType,
                        startDate = startDate,
                        endDate = endDate,
                        page = page,
                        pageSize = pageSize
                    )

                    call.ok(response)
                }
            }
        }
    }
}
