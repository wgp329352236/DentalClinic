package com.dentalclinic.admin

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

/**
 * 配置 Admin Panel SPA 静态文件服务
 * /admin 路径下提供 Vue SPA 静态资源
 * 未匹配的路径返回 index.html (SPA fallback)
 */
fun Application.configureAdminStaticFiles() {
    routing {
        staticResources("/admin", "admin") {
            default("index.html")
        }
    }
}
