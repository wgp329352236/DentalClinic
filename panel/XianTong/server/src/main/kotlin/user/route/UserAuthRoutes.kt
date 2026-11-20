package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.AccountDeletedException
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
                            isNewUser = response.isNewUser,
                            phone = response.phone
                        )
                    ))
                } catch (e: AccountDeletedException) {
                    call.respond(ApiResponse<Unit>(code = 403, message = "账号已注销，无法登录"))
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

            /**
             * 已登录用户重新授权手机号并绑定到当前账号。
             * POST /api/v1/auth/bind-phone
             * Header: Authorization: Bearer <access_token>
             * Body: { "phoneCode": "getPhoneNumber 回调的动态令牌" }
             *
             * 场景：登录时取消了手机号授权（走静默登录），或后续需要更换手机号。
             * 换取失败返回 400；号码被占用由 cuser_uk_phone_hash 唯一约束兜底翻译成 409。
             */
            userAuthenticated {
                post("/bind-phone") {
                    val userId = call.userId()
                    try {
                        val request = call.receive<BindPhoneRequest>()
                        val phone = authService.bindPhone(userId, request.phoneCode)
                        if (phone != null) {
                            call.respond(
                                ApiResponse(code = 0, message = "绑定成功", data = mapOf("phone" to phone))
                            )
                        } else {
                            call.respond(ApiResponse<Unit>(code = 400, message = "获取微信手机号失败，请重试"))
                        }
                    } catch (e: Exception) {
                        // scene 只记 userId，不记 phoneCode：动态令牌属敏感凭证
                        val (code, message) = translateApiError(
                            logger, "[Auth]: 用户 $userId 绑定手机号", "绑定失败，请稍后重试", e
                        )
                        call.respond(ApiResponse<Unit>(code = code, message = message))
                    }
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
data class BindPhoneRequest(
    // getPhoneNumber 回调的动态令牌（必填，已登录用户重新授权手机号时上报）
    val phoneCode: String
)

@Serializable
data class WechatLoginResponseDto(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean,
    /** 本次登录解密得到的手机号（未授权/换取失败时为 null），前端同步全局态用 */
    val phone: String? = null
)
