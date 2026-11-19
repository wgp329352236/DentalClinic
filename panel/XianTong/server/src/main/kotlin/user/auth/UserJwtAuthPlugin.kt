package com.dentalclinic.user.auth

import com.dentalclinic.admin.util.AuthenticationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*

/** Attribute key for storing the authenticated user ID */
private val UserIdKey = AttributeKey<Long>("UserId")

/**
 * Route-scoped JWT authentication plugin for C-end users.
 * Validates Bearer token on every call and stores userId in call.attributes.
 */
val UserJwtAuthPlugin = createRouteScopedPlugin("UserJwtAuth") {
    onCall { call ->
        val authHeader = call.request.headers[HttpHeaders.Authorization]
            ?: throw AuthenticationException("缺少认证令牌")

        if (!authHeader.startsWith("Bearer ")) {
            throw AuthenticationException("缺少认证令牌")
        }

        val token = authHeader.removePrefix("Bearer ")
        val decoded = UserJwtConfig.verifyAccessToken(token)
            ?: throw AuthenticationException("无效的访问令牌")

        val userId = decoded.subject?.toLongOrNull()
            ?: throw AuthenticationException("令牌载荷无效")

        call.attributes.put(UserIdKey, userId)
    }
}

/**
 * Wraps a route block with JWT authentication.
 * All routes inside will require a valid Bearer token.
 *
 * Usage:
 * ```
 * route("/api/v1/profile") {
 *     userAuthenticated {
 *         get("/me") {
 *             val userId = call.userId()
 *             // ...
 *         }
 *     }
 * }
 * ```
 */
fun Route.userAuthenticated(build: Route.() -> Unit): Route {
    val route = createChild(UserAuthenticatedSelector())
    route.install(UserJwtAuthPlugin)
    route.build()
    return route
}

/**
 * Get the authenticated user ID from call attributes.
 * Only available inside `userAuthenticated { }` blocks.
 */
fun ApplicationCall.userId(): Long =
    attributes.getOrNull(UserIdKey)
        ?: throw AuthenticationException("未认证")

private class UserAuthenticatedSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(userAuthenticated)"
}
