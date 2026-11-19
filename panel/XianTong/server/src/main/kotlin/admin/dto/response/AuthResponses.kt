package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userInfo: UserInfoResponse,
    val menus: List<MenuTreeNode>,
    val permissions: List<String>
)

@Serializable
data class UserInfoResponse(
    val id: Long,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val avatar: String? = null,
    val roles: List<RoleSimple>
)

@Serializable
data class RoleSimple(
    val id: Long,
    val roleCode: String,
    val roleName: String
)

@Serializable
data class MenuTreeNode(
    val id: Long,
    val parentId: Long? = null,
    val menuName: String,
    val menuType: String,
    val routePath: String? = null,
    val component: String? = null,
    val permissionCode: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isVisible: Int = 1,
    val isCache: Int = 0,
    val children: List<MenuTreeNode> = emptyList()
)

@Serializable
data class RefreshResponse(
    val accessToken: String
)
