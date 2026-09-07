package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.UserJwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*


/**
 * Helper: Extract and verify user ID from JWT token.
 * Returns the user ID on success, or null on failure (responds with 401).
 */
suspend fun authenticate(call: ApplicationCall): Long? {
    val authHeader = call.request.headers[HttpHeaders.Authorization]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
        return null
    }

    val token = authHeader.removePrefix("Bearer ")
    val decoded = UserJwtConfig.verifyAccessToken(token)
    if (decoded == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
        return null
    }

    val userId = decoded.subject?.toLongOrNull()
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(code = 401, message = "令牌载荷无效"))
        return null
    }

    return userId
}
