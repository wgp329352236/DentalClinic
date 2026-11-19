package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val email: String? = null,
    val roleIds: List<Long>,
    /** 授权可访问的门店ID列表；空表示该账号看不到任何门店业务数据（除非角色含 clinic:data:all） */
    val clinicIds: List<Long> = emptyList()
)

@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val email: String? = null,
    val roleIds: List<Long>? = null,
    /** 为 null 时不改动门店授权；非 null（含空列表）时全量覆盖 */
    val clinicIds: List<Long>? = null
)

@Serializable
data class ResetPasswordRequest(
    val newPassword: String
)

@Serializable
data class UpdateStatusRequest(
    val status: Int // 1=启用, 0=禁用
)
