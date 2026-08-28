package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.PermissionGroup
import com.dentalclinic.admin.dto.response.PermissionItem
import com.dentalclinic.admin.dto.response.RoleDetail
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.toDisplayString
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

class AdminRoleService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction(DBConfig.database) { block() }

    suspend fun listRoles(): List<RoleDetail> = dbQuery {
        AdminRoles.selectAll().orderBy(AdminRoles.id).toList().map { row ->
            val roleId = row[AdminRoles.id]
            RoleDetail(
                id = roleId,
                roleCode = row[AdminRoles.roleCode],
                roleName = row[AdminRoles.roleName],
                description = row[AdminRoles.description],
                isSystem = row[AdminRoles.isSystem],
                permissions = getRolePermissions(roleId),
                createdAt = row[AdminRoles.createdAt].toDisplayString() ?: ""
            )
        }
    }

    suspend fun getRole(id: Long): RoleDetail = dbQuery {
        val row = AdminRoles.selectAll().where { AdminRoles.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("角色不存在")
        RoleDetail(
            id = row[AdminRoles.id],
            roleCode = row[AdminRoles.roleCode],
            roleName = row[AdminRoles.roleName],
            description = row[AdminRoles.description],
            isSystem = row[AdminRoles.isSystem],
            permissions = getRolePermissions(id),
            createdAt = row[AdminRoles.createdAt].toDisplayString() ?: ""
        )
    }

    suspend fun createRole(roleCode: String, roleName: String, description: String?, permissionIds: List<Long>): Long =
        dbQuery {
            // 唯一性检查
            val exists = AdminRoles.selectAll().where { AdminRoles.roleCode eq roleCode }.count() > 0
            if (exists) throw ConflictException("角色编码已存在: $roleCode")

            val roleId = AdminRoles.insert {
                it[AdminRoles.roleCode] = roleCode
                it[AdminRoles.roleName] = roleName
                it[AdminRoles.description] = description
            }[AdminRoles.id]

            // 分配权限
            if (permissionIds.isNotEmpty()) {
                AdminRolePermissions.batchInsert(permissionIds, body = { permId ->
                    this[AdminRolePermissions.roleId] = roleId
                    this[AdminRolePermissions.permissionId] = permId
                })
            }
            roleId
        }

    suspend fun updateRole(id: Long, roleName: String?, description: String?, permissionIds: List<Long>?) = dbQuery {
        val role = AdminRoles.selectAll().where { AdminRoles.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("角色不存在")

        AdminRoles.update({ AdminRoles.id eq id }) {
            if (roleName != null) it[AdminRoles.roleName] = roleName
            if (description != null) it[AdminRoles.description] = description
        }

        // 更新权限分配
        if (permissionIds != null) {
            AdminRolePermissions.deleteWhere { roleId eq id }
            if (permissionIds.isNotEmpty()) {
                AdminRolePermissions.batchInsert(permissionIds, body = { permId ->
                    this[AdminRolePermissions.roleId] = id
                    this[AdminRolePermissions.permissionId] = permId
                })
            }
        }
    }

    suspend fun deleteRole(id: Long) = dbQuery {
        val role = AdminRoles.selectAll().where { AdminRoles.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("角色不存在")

        if (role[AdminRoles.isSystem] == 1) {
            throw ValidationException("系统内置角色不可删除")
        }

        AdminRolePermissions.deleteWhere { roleId eq id }
        AdminRoleMenus.deleteWhere { roleId eq id }
        AdminUserRoles.deleteWhere { roleId eq id }
        AdminRoles.deleteWhere { AdminRoles.id eq id }
    }

    suspend fun getAllPermissions(): List<PermissionGroup> = dbQuery {
        AdminPermissions.selectAll()
            .orderBy(AdminPermissions.module to SortOrder.ASC, AdminPermissions.id to SortOrder.ASC)
            .toList()
            .map { row ->
                PermissionItem(
                    id = row[AdminPermissions.id],
                    permissionCode = row[AdminPermissions.permissionCode],
                    permissionName = row[AdminPermissions.permissionName],
                    module = row[AdminPermissions.module],
                    description = row[AdminPermissions.description]
                )
            }
            .groupBy { it.module }
            .map { (module, perms) -> PermissionGroup(module = module, permissions = perms) }
    }

    private suspend fun getRolePermissions(roleId: Long): List<PermissionItem> {
        return AdminRolePermissions
            .join(
                AdminPermissions, JoinType.INNER,
                AdminRolePermissions.permissionId, AdminPermissions.id
            )
            .selectAll().where { AdminRolePermissions.roleId eq roleId }
            .toList()
            .map { row ->
                PermissionItem(
                    id = row[AdminPermissions.id],
                    permissionCode = row[AdminPermissions.permissionCode],
                    permissionName = row[AdminPermissions.permissionName],
                    module = row[AdminPermissions.module],
                    description = row[AdminPermissions.description]
                )
            }
    }
}
