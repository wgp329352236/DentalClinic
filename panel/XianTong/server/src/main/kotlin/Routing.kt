package com.dentalclinic

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Root routing configuration with CORS enabled.
 * Admin APIs are served under /api/admin via AdminModule.
 */
fun Application.configureRouting() {
    install(CORS) {
        allowHost("localhost:5173")       // Vite dev server default port
        allowHost("localhost:8080")
        allowHost("localhost:3000")
        allowHost("localhost:5666")       // Vben web-antd dev server（vite.config 固定端口）
        allowHost("127.0.0.1:5666")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }

    routing {
        get("/") {
            call.respondText("Dental Clinic API")
        }
    }
}
