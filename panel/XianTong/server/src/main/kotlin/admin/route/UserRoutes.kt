package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.request.CreateUserRequest
import com.dentalclinic.admin.dto.request.ResetPasswordRequest
import com.dentalclinic.admin.dto.request.UpdateStatusRequest
import com.dentalclinic.admin.dto.request.UpdateUserRequest
import com.dentalclinic.admin.service.AdminUserService
import com.dentalclinic.admin.util.PageQuery
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.admin.util.okPage
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: AdminUserService) {

    route("/users") {
        authenticated {

            requirePermission("user:list") {
                // GET /api/admin/users
                get {
                    val pq = PageQuery.from(call)
                    val roleId = call.parameters["roleId"]?.toLongOrNull()
                    val (users, total) = userService.listUsers(pq.page, pq.pageSize, pq.keyword, roleId)
                    call.okPage(users, total, pq.page, pq.pageSize)
                }

                // GET /api/admin/users/{id}
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的用户 ID")
                    val user = userService.getUser(id)
                    call.ok(user)
                }
            }

            requirePermission("user:create") {
                // POST /api/admin/users
                post {
                    val req = call.receive<CreateUserRequest>()
                    val id = userService.createUser(
                        req.username, req.password, req.displayName, req.email, req.roleIds, req.clinicIds
                    )
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            requirePermission("user:update") {
                // PUT /api/admin/users/{id}
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的用户 ID")
                    val req = call.receive<UpdateUserRequest>()
                    userService.updateUser(id, req.displayName, req.email, req.roleIds, req.clinicIds)
                    call.ok<Unit>(message = "更新成功")
                }

                // PUT /api/admin/users/{id}/status
                put("/{id}/status") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的用户 ID")
                    val req = call.receive<UpdateStatusRequest>()
                    userService.updateStatus(id, req.status)
                    call.ok<Unit>(message = "状态更新成功")
                }

                // PUT /api/admin/users/{id}/password
                put("/{id}/password") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的用户 ID")
                    val req = call.receive<ResetPasswordRequest>()
                    userService.resetPassword(id, req.newPassword)
                    call.ok<Unit>(message = "密码重置成功")
                }
            }

            requirePermission("user:delete") {
                // DELETE /api/admin/users/{id}
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的用户 ID")
                    userService.deleteUser(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
