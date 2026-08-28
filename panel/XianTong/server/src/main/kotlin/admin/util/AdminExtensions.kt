package com.dentalclinic.admin.util

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * LocalDateTime 格式化为字符串
 */
fun LocalDateTime?.toDisplayString(): String? = this?.format(dateTimeFormatter)

/**
 * 统一成功响应
 */
suspend inline fun <reified T> ApplicationCall.ok(data: T? = null, message: String = "success") {
    respond(HttpStatusCode.OK, ApiResponse(code = 0, message = message, data = data))
}

/**
 * 统一分页响应
 */
suspend inline fun <reified T> ApplicationCall.okPage(list: List<T>, total: Long, page: Int, pageSize: Int) {
    respond(
        HttpStatusCode.OK, ApiResponse(
            code = 0,
            message = "success",
            data = PageResponse(list = list, total = total, page = page, pageSize = pageSize)
        )
    )
}

/**
 * 敏感值脱敏：只显示最后 4 位
 */
fun maskSensitiveValue(value: String): String {
    if (value.length <= 4) return "****"
    return "*".repeat(value.length - 4) + value.takeLast(4)
}
