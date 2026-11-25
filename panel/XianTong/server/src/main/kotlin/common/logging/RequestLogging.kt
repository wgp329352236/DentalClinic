package com.dentalclinic.common.logging

import com.dentalclinic.admin.auth.AdminPrincipalKey
import com.dentalclinic.user.auth.userIdOrNull
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

/** 请求追踪 ID 响应头：客户端报障时带上它，可定位整条请求日志 */
const val REQUEST_ID_HEADER = "X-Request-Id"

/** MDC 键名，被 logback pattern 的 %X{requestId} 引用 */
const val REQUEST_ID_MDC_KEY = "requestId"

private val RequestIdKey = AttributeKey<String>("RequestId")

/** 只接受客户端传来的合法 ID，防止注入换行/超长内容污染日志 */
private val VALID_REQUEST_ID = Regex("^[A-Za-z0-9._-]{8,64}$")

/** 静态资源与图标会淹没业务请求，不记访问日志 */
private val SKIPPED_PATHS = listOf("/favicon.ico", "/assets/", "/static/")

/** query 中禁止落日志的键：凭证与个人敏感信息 */
private val SENSITIVE_QUERY_KEYS = setOf(
    "token", "accesstoken", "refreshtoken", "code", "phone", "password", "secret",
    "openid", "unionid", "sessionkey", "authorization"
)

/**
 * 访问日志插件：每个请求分配/沿用追踪 ID，完成后输出一行「方法 路径 状态码 耗时 来源 操作者」。
 *
 * 追踪 ID 同时写入 MDC，Ktor 会在请求处理全程注入，因此业务日志与异常日志都会带上同一个 requestId；
 * 客户端也可从响应头 [REQUEST_ID_HEADER] 拿到它，报障时直接提供即可串联整条链路。
 *
 * 日志中不出现 Authorization、请求体与手机号等敏感信息。
 */
fun Application.installRequestLogging() {
    intercept(ApplicationCallPipeline.Setup) {
        val requestId = call.request.headers[REQUEST_ID_HEADER]
            ?.trim()
            ?.takeIf(VALID_REQUEST_ID::matches)
            ?: UUID.randomUUID().toString()
        call.attributes.put(RequestIdKey, requestId)
        call.response.header(REQUEST_ID_HEADER, requestId)
    }

    install(CallLogging) {
        logger = LoggerFactory.getLogger("HttpRequest")
        level = Level.INFO
        disableDefaultColors()
        disableForStaticContent()
        filter { call -> SKIPPED_PATHS.none { call.request.path().startsWith(it) } }
        mdc(REQUEST_ID_MDC_KEY) { call -> call.attributes.getOrNull(RequestIdKey) }
        format(::formatAccessLog)
    }
}

/** 当前请求的追踪 ID；供异常处理等基础设施补充上下文 */
fun ApplicationCall.requestIdOrNull(): String? = attributes.getOrNull(RequestIdKey)

private fun formatAccessLog(call: ApplicationCall): String = buildString {
    append(call.request.httpMethod.value).append(' ').append(call.request.path())
    sanitizedQuery(call.request).takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
    append(" -> ").append(call.response.status()?.value ?: 0)
    append(' ').append(runCatching { call.processingTimeMillis() }.getOrDefault(-1)).append("ms")
    append(" ip=").append(clientIp(call.request))
    append(" actor=").append(actor(call))
    call.request.headers[HttpHeaders.UserAgent]
        ?.take(80)
        ?.takeIf { it.isNotBlank() }
        ?.let { append(" ua=").append(it) }
}

private fun actor(call: ApplicationCall): String =
    call.attributes.getOrNull(AdminPrincipalKey)?.let { "admin#${it.userId}" }
        ?: call.userIdOrNull()?.let { "user#$it" }
        ?: "-"

private fun clientIp(request: ApplicationRequest): String =
    (
        request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.headers["X-Real-IP"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.local.remoteAddress
        ).take(45)

private fun sanitizedQuery(request: ApplicationRequest): String {
    val params = request.queryParameters
    if (params.isEmpty()) return ""
    return params.names().joinToString("&") { name ->
        val value = if (name.lowercase() in SENSITIVE_QUERY_KEYS) {
            "***"
        } else {
            params.getAll(name).orEmpty().joinToString(",").take(120)
        }
        "$name=$value"
    }
}
