package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.ProfileUpdateResult
import com.dentalclinic.user.service.UpdateProfileRequest
import com.dentalclinic.user.service.UserProfileService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserProfileRoutes")

/**
 * User profile routes (protected by JWT).
 */
fun Application.userProfileRoutes(profileService: UserProfileService) {
    routing {
        route("/api/v1/profile") {
            userAuthenticated {
            /**
             * Get current user's profile.
             * GET /api/v1/profile/me
             * Header: Authorization: Bearer <access_token>
             *
             * 返回体含钱包余额与会员等级信息（等级码/名称/折扣/权益/累计消费/距升级差额），
             * 供小程序「我的」页渲染等级卡片与升级进度条。
             */
            get("/me") {
                val userId = call.userId()

                try {
                    val profile = profileService.getProfile(userId)
                    if (profile != null) {
                        call.respond(ApiResponse(code = 0, message = "success", data = profile))
                    } else {
                        call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                    }
                } catch (e: Exception) {
                    // scene 里只记 userId：资料查询不涉及请求体，无需担心敏感信息落日志
                    val (code, message) = translateApiError(
                        logger, "[Profile]: 用户 $userId 查询资料", "查询失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * Update user's profile information.
             * PUT /api/v1/profile/me
             * Body: { "realName": "张三", "phone": "13800138000", "gender": 1, "age": 25, "avatar": "url" }
             *
             * 手机号只做格式校验、不做验证码校验：短信通道与真实微信 code2Session 尚未接入。
             * 号码被占用时由 cuser_uk_phone_hash 盲索引唯一约束兜底，translateApiError 翻译成 409 友好文案。
             */
            put("/me") {
                val userId = call.userId()

                try {
                    val request = call.receive<UpdateProfileRequest>()

                    when (val result = profileService.updateProfile(userId, request)) {
                        is ProfileUpdateResult.Success ->
                            call.respond(ApiResponse<Unit>(code = 0, message = "更新成功"))
                        is ProfileUpdateResult.NotFound ->
                            call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                        is ProfileUpdateResult.Error ->
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                } catch (e: Exception) {
                    // scene 里只记 userId，不记 request：手机号/真实姓名属个人敏感信息，不应落入日志
                    val (code, message) = translateApiError(
                        logger, "[Profile]: 用户 $userId 更新资料", "更新失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 导出当前用户全部个人数据（需求 5.2 数据可携带权）。
             * GET /api/v1/profile/export
             */
            get("/export") {
                val userId = call.userId()
                try {
                    val data = profileService.exportProfile(userId)
                    if (data != null) {
                        call.respond(ApiResponse(code = 0, message = "success", data = data))
                    } else {
                        call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Profile]: 用户 $userId 导出数据", "导出失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 注销当前账户（需求 5.2 用户数据删除，软删除置 status=0）。
             * DELETE /api/v1/profile/me
             */
            delete("/me") {
                val userId = call.userId()
                try {
                    val ok = profileService.deleteAccount(userId)
                    if (ok) {
                        call.respond(ApiResponse<Unit>(code = 0, message = "账户已注销"))
                    } else {
                        call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Profile]: 用户 $userId 注销账户", "注销失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
            }
        }
    }
}
