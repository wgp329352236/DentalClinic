package com.dentalclinic.admin.util

import com.dentalclinic.admin.auth.AdminPrincipalKey
import com.dentalclinic.admin.entity.AdminAuditLogs
import com.dentalclinic.config.DBConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val auditLogger = LoggerFactory.getLogger("AuditInterceptor")

/**
 * 审计日志拦截插件
 * 记录所有写操作（POST/PUT/DELETE）到 admin_audit_log
 */
val AuditPlugin = createRouteScopedPlugin("AuditPlugin") {
    onCallRespond { call, _ ->
        val method = call.request.httpMethod
        // 只记录写操作
        if (method != HttpMethod.Post && method != HttpMethod.Put && method != HttpMethod.Delete) return@onCallRespond

        val path = call.request.path()
        // 只记录 admin API
        if (!path.startsWith("/api/admin")) return@onCallRespond
        // 跳过登录/刷新（这些在 AuthService 中单独记录）
        if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) return@onCallRespond

        try {
            val principal = call.attributes.getOrNull(AdminPrincipalKey)
            val username = principal?.username ?: "anonymous"
            val userId = principal?.userId

            val operationType = when (method) {
                HttpMethod.Post -> "create"
                HttpMethod.Put -> "update"
                HttpMethod.Delete -> "delete"
                else -> "unknown"
            }

            // 从路径中提取目标资源和 ID
            val pathParts = path.removePrefix("/api/admin/").split("/")
            val targetResource = pathParts.firstOrNull() ?: "unknown"
            val targetId = if (pathParts.size > 1) pathParts[1] else null

            val ipAddress = call.request.header("X-Forwarded-For")
                ?: call.request.header("X-Real-IP")
                ?: call.request.local.remoteAddress
            val userAgent = call.request.userAgent()

            val responseCode = call.response.status()?.value

            suspendTransaction(DBConfig.database) {
                AdminAuditLogs.insert {
                    it[AdminAuditLogs.userId] = userId
                    it[AdminAuditLogs.username] = username
                    it[AdminAuditLogs.operationType] = operationType
                    it[AdminAuditLogs.targetResource] = targetResource
                    it[AdminAuditLogs.targetId] = targetId
                    it[AdminAuditLogs.ipAddress] = ipAddress
                    it[AdminAuditLogs.userAgent] = userAgent?.take(255)
                    it[AdminAuditLogs.responseCode] = responseCode
                    it[AdminAuditLogs.createdAt] = LocalDateTime.now()
                }
            }
        } catch (e: Exception) {
            auditLogger.error("[Audit]: 记录审计日志失败", e)
        }
    }
}

/**
 * 记录认证事件审计日志
 */
suspend fun recordAuthAudit(
    username: String,
    operationType: String, // login, login_failed, logout, lockout
    ipAddress: String? = null,
    userId: Long? = null
) {
    try {
        suspendTransaction(DBConfig.database) {
            AdminAuditLogs.insert {
                it[AdminAuditLogs.userId] = userId
                it[AdminAuditLogs.username] = username
                it[AdminAuditLogs.operationType] = operationType
                it[AdminAuditLogs.targetResource] = "auth"
                it[AdminAuditLogs.ipAddress] = ipAddress
                it[AdminAuditLogs.createdAt] = LocalDateTime.now()
            }
        }
    } catch (e: Exception) {
        auditLogger.error("[Audit]: 记录认证审计日志失败", e)
    }
}
