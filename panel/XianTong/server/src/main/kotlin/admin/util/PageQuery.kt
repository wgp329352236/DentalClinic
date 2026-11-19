package com.dentalclinic.admin.util

import io.ktor.server.application.*

/**
 * 分页参数，从请求 query parameters 中提取
 */
data class PageQuery(
    val page: Int = 1,
    val pageSize: Int = 20,
    val keyword: String? = null
) {
    val offset: Long get() = ((page - 1) * pageSize).toLong()

    companion object {
        fun from(call: ApplicationCall): PageQuery {
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 20
            val keyword = call.parameters["keyword"]?.takeIf { it.isNotBlank() }
            return PageQuery(
                page = page.coerceAtLeast(1),
                pageSize = pageSize.coerceIn(1, 100),
                keyword = keyword
            )
        }
    }
}
