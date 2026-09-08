package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * User notification routes (protected by JWT).
 */
fun Application.userNotificationRoutes() {
    val notificationService = NotificationService()

    routing {
        route("/api/v1/messages") {
            userAuthenticated {
            /**
             * Get notification list.
             * GET /api/v1/messages?page=1&pageSize=20&msgType=system&isRead=false
             */
            get("") {
                val userId = call.userId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val msgType = call.request.queryParameters["msgType"]
                val isReadParam = call.request.queryParameters["isRead"]
                val isRead = when (isReadParam) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }

                val response = notificationService.getNotifications(userId, msgType, isRead, page, pageSize)
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
             * Get unread message count.
             * GET /api/v1/messages/unread-count
             */
            get("/unread-count") {
                val userId = call.userId()
                val count = notificationService.getUnreadCount(userId)
                call.respond(ApiResponse(code = 0, message = "success", data = mapOf("count" to count)))
            }

            /**
             * Mark a notification as read.
             * PUT /api/v1/messages/{id}/read
             */
            put("/{id}/read") {
                val userId = call.userId()
                val notificationId = call.parameters["id"]?.toLongOrNull()

                if (notificationId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的消息ID"))
                    return@put
                }

                val success = notificationService.markAsRead(userId, notificationId)
                if (success) {
                    call.respond(ApiResponse<Unit>(code = 0, message = "标记已读成功"))
                } else {
                    call.respond(ApiResponse<Unit>(code = 404, message = "消息不存在或已读"))
                }
            }

            /**
             * Mark all notifications as read.
             * PUT /api/v1/messages/read-all
             */
            put("/read-all") {
                val userId = call.userId()
                val count = notificationService.markAllAsRead(userId)
                call.respond(ApiResponse(code = 0, message = "全部标记已读", data = mapOf("markedCount" to count)))
            }
            }
        }
    }
}
