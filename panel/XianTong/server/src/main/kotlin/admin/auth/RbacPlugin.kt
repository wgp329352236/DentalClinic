package com.dentalclinic.admin.auth

import com.dentalclinic.admin.dto.response.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * RBAC 权限拦截插件配置
 */
class RbacPluginConfig {
    var requiredPermission: String = ""
}

/**
 * RBAC 权限检查插件
 * 在 JwtAuthInterceptor 之后执行，检查用户是否拥有指定权限
 */
val RbacInterceptor = createRouteScopedPlugin("RbacInterceptor", ::RbacPluginConfig) {
    val permission = pluginConfig.requiredPermission

    onCall { call ->
        val principal = call.attributes.getOrNull(AdminPrincipalKey)
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "未认证"))
            return@onCall
        }

        if (permission.isNotBlank() && permission !in principal.permissions) {
            call.respond(HttpStatusCode.Forbidden, ApiResponse<Unit>(code = 403, message = "权限不足: $permission"))
            return@onCall
        }
    }
}

/**
 * 路由级权限要求
 * 用法：
 * ```
 * authenticated {
 *     requirePermission("clinic:list") {
 *         get { ... }
 *     }
 * }
 * ```
 */
fun Route.requirePermission(permission: String, build: Route.() -> Unit): Route {
    val route = createChild(PermissionRouteSelector(permission))
    route.install(RbacInterceptor) { requiredPermission = permission }
    route.build()
    return route
}

private class PermissionRouteSelector(private val permission: String) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(permission:$permission)"
}
