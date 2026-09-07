package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.MenuTreeNode
import com.dentalclinic.admin.entity.AdminMenus
import com.dentalclinic.admin.entity.AdminRoleMenus
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class AdminMenuService : BaseService() {

    /**
     * 获取完整菜单树
     */
    suspend fun getMenuTree(): List<MenuTreeNode> = dbQuery {
        val allMenus = AdminMenus.selectAll()
            .orderBy(AdminMenus.sortOrder)
            .map { it.toMenuNode() }
            .toList()

        buildTree(allMenus, null)
    }

    /**
     * 创建菜单
     */
    suspend fun createMenu(
        parentId: Long?,
        menuName: String,
        menuType: String,
        routePath: String?,
        component: String?,
        permissionCode: String?,
        icon: String?,
        sortOrder: Int,
        isVisible: Int,
        isCache: Int
    ): Long = dbQuery {
        // 检查深度限制（最大 3 层）
        if (parentId != null) {
            val depth = getMenuDepth(parentId)
            if (depth >= 2) { // parentId 已经是第 2 层，再加一层就是第 3 层
                throw ValidationException("菜单层级不能超过 3 层")
            }
        }

        AdminMenus.insert {
            it[AdminMenus.parentId] = parentId
            it[AdminMenus.menuName] = menuName
            it[AdminMenus.menuType] = menuType
            it[AdminMenus.routePath] = routePath
            it[AdminMenus.component] = component
            it[AdminMenus.permissionCode] = permissionCode
            it[AdminMenus.icon] = icon
            it[AdminMenus.sortOrder] = sortOrder
            it[AdminMenus.isVisible] = isVisible
            it[AdminMenus.isCache] = isCache
        }[AdminMenus.id]
    }

    /**
     * 更新菜单
     */
    suspend fun updateMenu(
        id: Long,
        parentId: Long?,
        menuName: String?,
        menuType: String?,
        routePath: String?,
        component: String?,
        permissionCode: String?,
        icon: String?,
        sortOrder: Int?,
        isVisible: Int?,
        isCache: Int?
    ) = dbQuery {
        AdminMenus.selectAll().where { AdminMenus.id eq id }.singleOrNull()
            ?: throw NotFoundException("菜单不存在")

        // 如果修改了 parentId，检查深度
        if (parentId != null) {
            val depth = getMenuDepth(parentId)
            if (depth >= 2) throw ValidationException("菜单层级不能超过 3 层")
        }

        AdminMenus.update({ AdminMenus.id eq id }) {
            if (parentId != null) it[AdminMenus.parentId] = parentId
            if (menuName != null) it[AdminMenus.menuName] = menuName
            if (menuType != null) it[AdminMenus.menuType] = menuType
            if (routePath != null) it[AdminMenus.routePath] = routePath
            if (component != null) it[AdminMenus.component] = component
            if (permissionCode != null) it[AdminMenus.permissionCode] = permissionCode
            if (icon != null) it[AdminMenus.icon] = icon
            if (sortOrder != null) it[AdminMenus.sortOrder] = sortOrder
            if (isVisible != null) it[AdminMenus.isVisible] = isVisible
            if (isCache != null) it[AdminMenus.isCache] = isCache
        }
    }

    /**
     * 删除菜单（级联删除子菜单和角色关联）
     */
    suspend fun deleteMenu(id: Long) = dbQuery {
        AdminMenus.selectAll().where { AdminMenus.id eq id }.singleOrNull()
            ?: throw NotFoundException("菜单不存在")

        // 递归删除子菜单
        deleteMenuRecursive(id)
    }

    private suspend fun deleteMenuRecursive(menuId: Long) {
        // 先删除子菜单
        val children = AdminMenus.selectAll().where { AdminMenus.parentId eq menuId }
            .map { it[AdminMenus.id] }
            .toList()
        children.forEach { deleteMenuRecursive(it) }

        // 删除角色-菜单关联
        AdminRoleMenus.deleteWhere { this.menuId eq menuId }
        // 删除菜单本身
        AdminMenus.deleteWhere { AdminMenus.id eq menuId }
    }

    /**
     * 获取菜单深度（0-based）
     */
    private suspend fun getMenuDepth(menuId: Long): Int {
        var depth = 0
        var currentId: Long? = menuId
        while (currentId != null) {
            depth++
            currentId = AdminMenus.selectAll().where { AdminMenus.id eq currentId!! }
                .singleOrNull()?.get(AdminMenus.parentId)
        }
        return depth
    }

    private fun buildTree(nodes: List<MenuTreeNode>, parentId: Long?): List<MenuTreeNode> {
        return nodes.filter { it.parentId == parentId }
            .map { node -> node.copy(children = buildTree(nodes, node.id)) }
    }

    private fun ResultRow.toMenuNode() = MenuTreeNode(
        id = this[AdminMenus.id],
        parentId = this[AdminMenus.parentId],
        menuName = this[AdminMenus.menuName],
        menuType = this[AdminMenus.menuType],
        routePath = this[AdminMenus.routePath],
        component = this[AdminMenus.component],
        permissionCode = this[AdminMenus.permissionCode],
        icon = this[AdminMenus.icon],
        sortOrder = this[AdminMenus.sortOrder],
        isVisible = this[AdminMenus.isVisible],
        isCache = this[AdminMenus.isCache]
    )
}
