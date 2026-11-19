package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.request.CreateRoleRequest
import com.dentalclinic.admin.dto.request.UpdateRoleRequest
import com.dentalclinic.admin.service.AdminRoleService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.roleRoutes(roleService: AdminRoleService) {

    route("/roles") {
        authenticated {

            requirePermission("role:list") {
                // GET /api/admin/roles
                get {
                    val roles = roleService.listRoles()
                    call.ok(roles)
                }

                // GET /api/admin/roles/{id}
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的角色 ID")
                    val role = roleService.getRole(id)
                    call.ok(role)
                }

                // GET /api/admin/roles/permissions - 获取所有权限（按模块分组）
                get("/permissions") {
                    val permissions = roleService.getAllPermissions()
                    call.ok(permissions)
                }
            }

            requirePermission("role:create") {
                // POST /api/admin/roles
                post {
                    val req = call.receive<CreateRoleRequest>()
                    val id = roleService.createRole(
                        req.roleCode, req.roleName, req.description, req.permissionIds, call.clinicScope()
                    )
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            requirePermission("role:update") {
                // PUT /api/admin/roles/{id}
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的角色 ID")
                    val req = call.receive<UpdateRoleRequest>()
                    roleService.updateRole(id, req.roleName, req.description, req.permissionIds, call.clinicScope())
                    call.ok<Unit>(message = "更新成功")
                }
            }

            requirePermission("role:delete") {
                // DELETE /api/admin/roles/{id}
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的角色 ID")
                    roleService.deleteRole(id, call.clinicScope())
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
