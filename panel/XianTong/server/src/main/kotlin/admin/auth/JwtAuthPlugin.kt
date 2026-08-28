package com.dentalclinic.admin.auth

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.util.AuthenticationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * JWT 认证 Principal，存储在 call.attributes 中
 */
data class AdminPrincipal(
    val userId: Long,
    val username: String,
    val permissions: List<String>
)

/** Attribute key for storing the admin principal */
val AdminPrincipalKey = AttributeKey<AdminPrincipal>("AdminPrincipal")

/**
 * JWT 认证拦截插件
 * 验证 Authorization: Bearer {token}，提取用户信息存入 call.attributes
 */
val JwtAuthInterceptor = createRouteScopedPlugin("JwtAuthInterceptor") {
    onCall { call ->
        val authHeader = call.request.header(HttpHeaders.Authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
            return@onCall
        }

        val token = authHeader.removePrefix("Bearer ")
        val jwt = JwtConfig.verifyToken(token)
        if (jwt == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "令牌无效或已过期"))
            return@onCall
        }

        if (JwtConfig.getTokenType(jwt) != "access") {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "无效的令牌类型"))
            return@onCall
        }

        val principal = AdminPrincipal(
            userId = JwtConfig.getUserId(jwt),
            username = JwtConfig.getUsername(jwt),
            permissions = JwtConfig.getPermissions(jwt)
        )
        call.attributes.put(AdminPrincipalKey, principal)
    }
}

/**
 * 从 ApplicationCall 中获取当前认证用户
 */
fun ApplicationCall.adminPrincipal(): AdminPrincipal =
    attributes.getOrNull(AdminPrincipalKey)
        ?: throw AuthenticationException("未认证")

/**
 * 认证路由包装：需要有效 JWT
 */
fun Route.authenticated(build: Route.() -> Unit): Route {
    val route = createChild(AuthenticatedRouteSelector())
    route.install(JwtAuthInterceptor)
    route.build()
    return route
}

private class AuthenticatedRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(authenticated)"
}
