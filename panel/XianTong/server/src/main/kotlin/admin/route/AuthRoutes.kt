package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.dto.request.LoginRequest
import com.dentalclinic.admin.dto.request.RefreshTokenRequest
import com.dentalclinic.admin.service.AdminAuthService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AdminAuthService) {

    route("/auth") {

        // POST /api/admin/auth/login - 用户登录（公开）
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request.username, request.password)
            call.ok(response)
        }

        // POST /api/admin/auth/refresh - 刷新 Token（公开）
        // Vben Admin 期望响应: {code: 0, data: "newAccessToken", message: "success"}
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val response = authService.refresh(request.refreshToken)
            call.ok(response.accessToken)
        }

        // 需要认证的接口
        authenticated {

            // PUT /api/admin/auth/password - 修改密码
            put("/password") {
                val principal = call.adminPrincipal()
                val body = call.receive<Map<String, String>>()
                val oldPassword = body["oldPassword"] ?: throw ValidationException("缺少旧密码")
                val newPassword = body["newPassword"] ?: throw ValidationException("缺少新密码")
                authService.changePassword(principal.userId, oldPassword, newPassword)
                call.ok<Unit>(message = "密码修改成功")
            }

            // POST /api/admin/auth/logout - 退出登录
            post("/logout") {
                val request = call.receive<RefreshTokenRequest>()
                authService.logout(request.refreshToken)
                call.ok<Unit>(message = "已退出登录")
            }

            // GET /api/admin/auth/profile - 获取当前用户信息
            get("/profile") {
                val principal = call.adminPrincipal()
                val profile = authService.getProfile(principal.userId)
                call.ok(profile)
            }
        }
    }
}
