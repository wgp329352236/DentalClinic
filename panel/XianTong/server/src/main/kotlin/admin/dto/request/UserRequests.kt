package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val email: String? = null,
    val roleIds: List<Long>
)

@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val email: String? = null,
    val roleIds: List<Long>? = null
)

@Serializable
data class ResetPasswordRequest(
    val newPassword: String
)

@Serializable
data class UpdateStatusRequest(
    val status: Int // 1=启用, 0=禁用
)
