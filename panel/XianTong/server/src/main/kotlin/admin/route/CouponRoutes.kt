package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminCouponService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Admin coupon management routes.
 */
fun Route.couponRoutes(couponService: AdminCouponService) {
    route("/coupons") {
        authenticated {
            // GET /api/admin/coupons/templates - Get all coupon templates
            requirePermission("coupon:view") {
                get("/templates") {
                    val templates = couponService.getCouponTemplates()
                    call.ok(templates)
                }
            }

            // POST /api/admin/coupons/templates - Create a new coupon template
            requirePermission("coupon:manage") {
                post("/templates") {
                    val request = call.receive<com.dentalclinic.admin.service.CreateCouponTemplateRequest>()

                    // Validate input
                    if (request.name.isBlank()) {
                        throw ValidationException("优惠券名称不能为空")
                    }
                    if (request.faceValue <= 0) {
                        throw ValidationException("面额必须大于0")
                    }
                    if (request.totalQuantity <= 0) {
                        throw ValidationException("发放数量必须大于0")
                    }

                    val id = couponService.createCouponTemplate(request)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            // PUT /api/admin/coupons/templates/{id} - Update a coupon template
            requirePermission("coupon:manage") {
                put("/templates/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的模板ID")

                    val request = call.receive<com.dentalclinic.admin.service.UpdateCouponTemplateRequest>()

                    // Validate input
                    if (request.name.isBlank()) {
                        throw ValidationException("优惠券名称不能为空")
                    }
                    if (request.faceValue <= 0) {
                        throw ValidationException("面额必须大于0")
                    }

                    couponService.updateCouponTemplate(id, request)
                    call.ok<Unit>(message = "更新成功")
                }
            }

            // POST /api/admin/coupons/grant - Grant coupons to users
            requirePermission("coupon:manage") {
                post("/grant") {
                    val request = call.receive<com.dentalclinic.admin.service.GrantCouponsRequest>()
                    val principal = call.adminPrincipal()

                    if (request.userIds.isEmpty()) {
                        throw ValidationException("用户列表不能为空")
                    }

                    val result = couponService.grantCouponsToUsers(
                        templateId = request.templateId,
                        userIds = request.userIds,
                        operator = principal.username
                    )

                    when (result) {
                        is com.dentalclinic.admin.service.GrantResult.Success -> {
                            call.ok(mapOf("successCount" to result.count), "发放成功")
                        }
                        is com.dentalclinic.admin.service.GrantResult.PartialSuccess -> {
                            call.ok(
                                mapOf(
                                    "successCount" to result.successCount,
                                    "failedUsers" to result.failedUsers
                                ),
                                "部分发放成功"
                            )
                        }
                        is com.dentalclinic.admin.service.GrantResult.Error -> {
                            throw ValidationException(result.message)
                        }
                    }
                }
            }

            // GET /api/admin/coupons/statistics - Get coupon statistics
            requirePermission("coupon:view") {
                get("/statistics") {
                    val templateId = call.request.queryParameters["templateId"]?.toLongOrNull()
                    val statistics = couponService.getCouponStatistics(templateId)
                    call.ok(statistics)
                }
            }

            // POST /api/admin/coupons/scan-redeem - Scan and redeem coupon
            requirePermission("coupon:manage") {
                post("/scan-redeem") {
                    val request = call.receive<com.dentalclinic.admin.service.ScanRedeemRequest>()
                    val principal = call.adminPrincipal()

                    if (request.qrData.isBlank()) {
                        throw ValidationException("二维码数据不能为空")
                    }

                    val result = couponService.scanAndRedeemCoupon(
                        qrData = request.qrData,
                        operator = principal.username
                    )

                    when (result) {
                        is com.dentalclinic.admin.service.ScanRedeemResult.Success -> {
                            call.ok(
                                mapOf(
                                    "couponName" to result.couponName,
                                    "faceValue" to result.faceValue
                                ),
                                "核销成功"
                            )
                        }
                        is com.dentalclinic.admin.service.ScanRedeemResult.Error -> {
                            throw ValidationException(result.message)
                        }
                    }
                }
            }
        }
    }
}
