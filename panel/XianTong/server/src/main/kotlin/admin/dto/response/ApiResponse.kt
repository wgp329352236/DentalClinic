package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "success",
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T? = null, message: String = "success") =
            ApiResponse(code = 0, message = message, data = data)

        fun <T> error(code: Int, message: String, data: T? = null) =
            ApiResponse(code = code, message = message, data = data)
    }
}

@Serializable
data class PageResponse<T>(
    val list: List<T>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)
