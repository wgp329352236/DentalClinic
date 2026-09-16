package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.service.UserAuthService
import com.dentalclinic.user.service.UserAuthService.WxProfile
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserAuthRoutes")

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
                    val response = authService.wechatLogin(
                        code = request.code,
                        phoneCode = request.phoneCode,
                        profile = request.userInfo?.let {
                            WxProfile(
                                nickname = it.nickName,
                                avatarUrl = it.avatarUrl,
                                gender = it.gender
                            )
                        }
                    )

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
                    // scene 里不拼 request.code：微信登录凭证不应落入日志
                    val (code, message) = translateApiError(
                        logger, "[Auth]: 微信登录", "登录失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
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
                    // 同上：scene 里不拼 refreshToken
                    val (code, message) = translateApiError(
                        logger, "[Auth]: 刷新令牌", "刷新失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
        }
    }
}

@Serializable
data class WechatLoginRequest(
    val code: String,
    // getPhoneNumber 回调的动态令牌（可选，未授权手机号时为空）
    val phoneCode: String? = null,
    // wx.getUserProfile 兜底资料（可选）
    val userInfo: WxUserInfoDto? = null
)

@Serializable
data class WxUserInfoDto(
    val nickName: String? = null,
    val avatarUrl: String? = null,
    val gender: Int? = null
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class WechatLoginResponseDto(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean
)
