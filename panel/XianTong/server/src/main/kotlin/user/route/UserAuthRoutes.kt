package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.service.UserAuthService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * User-end authentication routes.
 */
fun Application.userAuthRoutes(authService: UserAuthService) {
    routing {
        route("/api/v1/auth") {
            /**
             * WeChat login/registration.
             * POST /api/v1/auth/wechat-login
             * Body: { "code": "wechat_js_code" }
             */
            post("/wechat-login") {
                try {
                    val request = call.receive<WechatLoginRequest>()
                    val response = authService.wechatLogin(request.code)

                    call.respond(ApiResponse(
                        code = 0,
                        message = if (response.isNewUser) "注册成功" else "登录成功",
                        data = WechatLoginResponseDto(
                            userId = response.userId,
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken,
                            isNewUser = response.isNewUser
                        )
                    ))
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(
                        code = 500,
                        message = "登录失败: ${e.message}"
                    ))
                }
            }

            /**
             * Refresh access token.
             * POST /api/v1/auth/refresh
             */
            post("/refresh") {
                try {
                    val request = call.receive<RefreshTokenRequest>()
                    val newToken = authService.refreshToken(request.refreshToken)

                    if (newToken != null) {
                        call.respond(ApiResponse(
                            code = 0,
                            message = "success",
                            data = mapOf("accessToken" to newToken)
                        ))
                    } else {
                        call.respond(ApiResponse<Unit>(
                            code = 401,
                            message = "无效的刷新令牌"
                        ))
                    }
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(
                        code = 500,
                        message = "刷新失败: ${e.message}"
                    ))
                }
            }
        }
    }
}

@Serializable
data class WechatLoginRequest(val code: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class WechatLoginResponseDto(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean
)
