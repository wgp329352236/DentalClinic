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
        }
    }
}
