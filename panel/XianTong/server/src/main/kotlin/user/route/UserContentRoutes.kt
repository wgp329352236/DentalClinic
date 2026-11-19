package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.service.ContentService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserContentRoutes")

/**
 * 小程序首页内容路由（公开，无需登录）。
 *
 * 放在 `userAuthenticated` 之外，与 /api/v1/clinics 同级：首页在未登录状态下也要展示
 * 轮播图与公告，否则新用户第一次进来看到的是一片空白。
 */
fun Application.userContentRoutes(contentService: ContentService) {
    routing {
        route("/api/v1/content") {
            /**
             * 首页内容：轮播图 + 公告。
             * GET /api/v1/content/home
             *
             * 只返回「启用中且处于生效排期内」的条目，停用与过期内容由后端过滤，
             * 不依赖小程序端自行判断时间。
             */
            get("/home") {
                try {
                    val content = contentService.getHomeContent()
                    call.respond(ApiResponse(code = 0, message = "success", data = content))
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Content]: 首页内容", "加载失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 活动详情（首页轮播图点击落地）。
             * GET /api/v1/content/activities/{id}
             *
             * 活动不存在、已停用或不在排期内统一回 404：前端只需提示「活动已结束」，
             * 不需要区分三种原因，也不暴露活动是否存在的更多信息。
             */
            get("/activities/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的活动ID"))
                    return@get
                }
                try {
                    val activity = contentService.getActivityDetail(id)
                    if (activity == null) {
                        call.respond(ApiResponse<Unit>(code = 404, message = "活动不存在或已结束"))
                    } else {
                        call.respond(ApiResponse(code = 0, message = "success", data = activity))
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Content]: 活动详情", "加载失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * 微信订阅消息模板配置（公开）。
             * GET /api/v1/content/subscribe-templates
             *
             * 预约提交成功后，小程序用这里返回的 templateId 调 wx.requestSubscribeMessage
             * 申请一次性订阅授权（晚间/晨间就诊提醒）。只返回已配置真实模板 ID 且启用的模板，
             * 未配置时返回空数组，小程序侧不弹授权框、提醒降级站内信。
             */
            get("/subscribe-templates") {
                try {
                    val templates = contentService.getSubscribeTemplates()
                    call.respond(ApiResponse(code = 0, message = "success", data = templates))
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[Content]: 订阅模板配置", "加载失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
        }
    }
}
