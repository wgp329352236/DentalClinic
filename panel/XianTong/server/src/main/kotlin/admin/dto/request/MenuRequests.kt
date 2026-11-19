package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateMenuRequest(
    val parentId: Long? = null,
    val menuName: String,
    val menuType: String, // directory, menu, button
    val routePath: String? = null,
    val component: String? = null,
    val permissionCode: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isVisible: Int = 1,
    val isCache: Int = 0
)

@Serializable
data class UpdateMenuRequest(
    val parentId: Long? = null,
    val menuName: String? = null,
    val menuType: String? = null,
    val routePath: String? = null,
    val component: String? = null,
    val permissionCode: String? = null,
    val icon: String? = null,
    val sortOrder: Int? = null,
    val isVisible: Int? = null,
    val isCache: Int? = null
)
