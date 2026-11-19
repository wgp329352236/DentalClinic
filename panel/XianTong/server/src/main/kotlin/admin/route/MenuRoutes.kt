package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.request.CreateMenuRequest
import com.dentalclinic.admin.dto.request.UpdateMenuRequest
import com.dentalclinic.admin.service.AdminMenuService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.menuRoutes(menuService: AdminMenuService) {

    route("/menus") {
        authenticated {

            requirePermission("menu:list") {
                // GET /api/admin/menus - 菜单树
                get {
                    val tree = menuService.getMenuTree()
                    call.ok(tree)
                }
            }

            requirePermission("menu:create") {
                // POST /api/admin/menus
                post {
                    val req = call.receive<CreateMenuRequest>()
                    val id = menuService.createMenu(
                        parentId = req.parentId,
                        menuName = req.menuName,
                        menuType = req.menuType,
                        routePath = req.routePath,
                        component = req.component,
                        permissionCode = req.permissionCode,
                        icon = req.icon,
                        sortOrder = req.sortOrder,
                        isVisible = req.isVisible,
                        isCache = req.isCache
                    )
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }

            requirePermission("menu:update") {
                // PUT /api/admin/menus/{id}
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的菜单 ID")
                    val req = call.receive<UpdateMenuRequest>()
                    menuService.updateMenu(
                        id = id,
                        parentId = req.parentId,
                        menuName = req.menuName,
                        menuType = req.menuType,
                        routePath = req.routePath,
                        component = req.component,
                        permissionCode = req.permissionCode,
                        icon = req.icon,
                        sortOrder = req.sortOrder,
                        isVisible = req.isVisible,
                        isCache = req.isCache
                    )
                    call.ok<Unit>(message = "更新成功")
                }
            }

            requirePermission("menu:delete") {
                // DELETE /api/admin/menus/{id}
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的菜单 ID")
                    menuService.deleteMenu(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
