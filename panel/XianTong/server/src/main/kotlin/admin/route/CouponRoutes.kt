package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminCouponService
import com.dentalclinic.admin.service.ScanRedeemRequest
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * 后台优惠券管理路由。
 */
fun Route.couponRoutes(couponService: AdminCouponService) {
    route("/coupons") {
        authenticated {
            // GET /api/admin/coupons/templates - 获取所有优惠券模板
            requirePermission("coupon:view") {
                get("/templates") {
                    val templates = couponService.getCouponTemplates()
                    call.ok(templates)
                }
            }

            // POST /api/admin/coupons/templates - 创建新的优惠券模板
            requirePermission("coupon:manage") {
                post("/templates") {
                    val request = call.receive<com.dentalclinic.admin.service.CreateCouponTemplateRequest>()

                    // 校验输入参数
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

            // PUT /api/admin/coupons/templates/{id} - 更新优惠券模板
            requirePermission("coupon:manage") {
                put("/templates/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的模板ID")

                    val request = call.receive<com.dentalclinic.admin.service.UpdateCouponTemplateRequest>()

                    // 校验输入参数
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

            // DELETE /api/admin/coupons/templates/{id} - 删除优惠券模板
            requirePermission("coupon:manage") {
                delete("/templates/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的模板ID")

                    when (val result = couponService.deleteCouponTemplate(id)) {
                        is com.dentalclinic.admin.service.DeleteCouponResult.Success -> {
                            call.ok<Unit>(message = "删除成功")
                        }
                        is com.dentalclinic.admin.service.DeleteCouponResult.Error -> {
                            throw ValidationException(result.message)
                        }
                    }
                }
            }

            // POST /api/admin/coupons/grant - 向用户发放优惠券
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

            // GET /api/admin/coupons/statistics - 获取优惠券统计数据
            requirePermission("coupon:view") {
                get("/statistics") {
                    val templateId = call.request.queryParameters["templateId"]?.toLongOrNull()
                    val statistics = couponService.getCouponStatistics(templateId)
                    call.ok(statistics)
                }
            }

            // GET /api/admin/coupons/records - 分页查询用户优惠券记录
            requirePermission("coupon:view") {
                get("/records") {
                    val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                    val templateId = call.request.queryParameters["templateId"]?.toLongOrNull()
                    val status = call.request.queryParameters["status"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                    val response = couponService.getUserCoupons(
                        userId = userId,
                        templateId = templateId,
                        status = status,
                        page = page.coerceAtLeast(1),
                        pageSize = pageSize.coerceIn(1, 100)
                    )
                    call.ok(response)
                }
            }

            // POST /api/admin/coupons/scan-redeem - 扫码核销优惠券
            requirePermission("coupon:manage") {
                post("/scan-redeem") {
                    val request = call.receive<ScanRedeemRequest>()
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
