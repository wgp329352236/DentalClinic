package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class UserListItem(
    val id: Long,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val status: Int,
    val roles: List<RoleSimple>,
    /** 账号被授权的门店ID列表（数据隔离范围，clinic:data:all 权限不受此限） */
    val clinicIds: List<Long> = emptyList(),
    val lastLoginAt: String? = null,
    val createdAt: String
)

@Serializable
data class RoleDetail(
    val id: Long,
    val roleCode: String,
    val roleName: String,
    val description: String? = null,
    val isSystem: Int,
    val permissions: List<PermissionItem>,
    val createdAt: String
)

@Serializable
data class PermissionItem(
    val id: Long,
    val permissionCode: String,
    val permissionName: String,
    val module: String,
    val description: String? = null
)

@Serializable
data class PermissionGroup(
    val module: String,
    val permissions: List<PermissionItem>
)
