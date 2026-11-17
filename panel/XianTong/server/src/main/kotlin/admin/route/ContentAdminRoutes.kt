package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminContentService
import com.dentalclinic.admin.service.SaveActivityRequest
import com.dentalclinic.admin.service.SaveBannerRequest
import com.dentalclinic.admin.service.SaveNoticeRequest
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * 小程序内容配置路由（需求 4.11：首页轮播图、公告内容维护）。
 *
 * 校验逻辑集中在 AdminContentService，路由层只做参数绑定与响应封装，
 * 与 couponRoutes 的分工保持一致。
 */
fun Route.contentAdminRoutes(contentService: AdminContentService) {

    // ==================== 活动 ====================
    route("/activities") {
        authenticated {
            // GET /api/admin/activities?status=1
            requirePermission("content:view") {
                get {
                    val status = call.request.queryParameters["status"]?.toIntOrNull()
                    call.ok(contentService.listActivities(status))
                }
            }

            requirePermission("content:manage") {
                post {
                    val request = call.receive<SaveActivityRequest>()
                    val operator = call.adminPrincipal().username
                    val id = contentService.createActivity(request, operator)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            requirePermission("content:manage") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的活动ID")
                    contentService.updateActivity(id, call.receive<SaveActivityRequest>())
                    call.ok<Unit>(message = "更新成功")
                }
            }

            requirePermission("content:manage") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的活动ID")
                    contentService.deleteActivity(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }

    // ==================== 轮播图 ====================
    route("/banners") {
        authenticated {
            // GET /api/admin/banners?status=1
            requirePermission("content:view") {
                get {
                    val status = call.request.queryParameters["status"]?.toIntOrNull()
                    call.ok(contentService.listBanners(status))
                }
            }

            // POST /api/admin/banners
            requirePermission("content:manage") {
                post {
                    val request = call.receive<SaveBannerRequest>()
                    val operator = call.adminPrincipal().username
                    val id = contentService.createBanner(request, operator)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            // PUT /api/admin/banners/{id}
            requirePermission("content:manage") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的轮播图ID")
                    val request = call.receive<SaveBannerRequest>()
                    contentService.updateBanner(id, request)
                    call.ok<Unit>(message = "更新成功")
                }
            }

            // DELETE /api/admin/banners/{id}
            requirePermission("content:manage") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的轮播图ID")
                    contentService.deleteBanner(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }

    // ==================== 公告 ====================
    route("/notices") {
        authenticated {
            // GET /api/admin/notices?status=1
            requirePermission("content:view") {
                get {
                    val status = call.request.queryParameters["status"]?.toIntOrNull()
                    call.ok(contentService.listNotices(status))
                }
            }

            // POST /api/admin/notices
            requirePermission("content:manage") {
                post {
                    val request = call.receive<SaveNoticeRequest>()
                    val operator = call.adminPrincipal().username
                    val id = contentService.createNotice(request, operator)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            // PUT /api/admin/notices/{id}
            requirePermission("content:manage") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的公告ID")
                    val request = call.receive<SaveNoticeRequest>()
                    contentService.updateNotice(id, request)
                    call.ok<Unit>(message = "更新成功")
                }
            }

            // DELETE /api/admin/notices/{id}
            requirePermission("content:manage") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的公告ID")
                    contentService.deleteNotice(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
