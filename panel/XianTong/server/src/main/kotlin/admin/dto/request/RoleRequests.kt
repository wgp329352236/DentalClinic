package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoleRequest(
    val roleCode: String,
    val roleName: String,
    val description: String? = null,
    val permissionIds: List<Long> = emptyList()
)

@Serializable
data class UpdateRoleRequest(
    val roleName: String? = null,
    val description: String? = null,
    val permissionIds: List<Long>? = null
)
