package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.common.config.GlobalConfig.appConfig
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserReferralRoutes")

/**
 * User referral routes (protected by JWT).
 */
fun Application.userReferralRoutes(referralService: ReferralService) {
    // 分享落地页域名外置到配置，避免把 example.com 占位地址硬编码在源码里随包上线。
    // 未配置时仍回落到占位值，以保持响应字段结构不变（小程序端不会因 shareLink 消失而报错），
    // 但启动即告警，把这个「上线前必须改」的项从代码注释里提到日志里。
    val placeholderShareBase = "https://example.com/register"
    val shareBaseUrl = appConfig.propertyOrNull("referral.shareBaseUrl")
        ?.getString()?.trim()?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }
        ?: placeholderShareBase
    if (shareBaseUrl == placeholderShareBase) {
        logger.warn(
            "[Referral]: 未配置 referral.shareBaseUrl，分享链接仍指向占位域名 $placeholderShareBase，上线前必须替换"
        )
    }

    routing {
        route("/api/v1/referral") {
            userAuthenticated {
            /**
             * Get user's referral code.
             * GET /api/v1/referral/code
             */
            get("/code") {
                val userId = call.userId()
                val code = referralService.getReferralCode(userId)
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = mapOf("referralCode" to code, "shareLink" to "$shareBaseUrl?code=$code")
                ))
            }

            /**
             * Get referral records (people referred by user).
             * GET /api/v1/referral/records?page=1&pageSize=20
             */
            get("/records") {
                val userId = call.userId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = referralService.getReferralRecords(userId, page, pageSize)
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = PageResponse(
                        list = response.list,
                        total = response.total,
                        page = response.page,
                        pageSize = response.pageSize
                    )
                ))
            }

            /**
             * Register with referral code (called during user registration).
             * POST /api/v1/referral/register
             */
            post("/register") {
                val userId = call.userId()

                try {
                    val request = call.receive<RegisterWithReferralRequest>()
                    val result = referralService.registerWithReferral(userId, request.referralCode)

                    when (result) {
                        is ReferralResult.Success -> {
                            call.respond(ApiResponse<Unit>(code = 0, message = "推荐关系绑定成功"))
                        }
                        is ReferralResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                } catch (e: Exception) {
                    // 业务错误已由 ReferralResult.Error 走 400 分支，落到这里的只能是底层异常
                    val (code, message) = translateApiError(
                        logger, "[Referral]: 用户 $userId 绑定推荐关系", "绑定失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
            }
        }
    }
}

@Serializable
data class RegisterWithReferralRequest(val referralCode: String)
