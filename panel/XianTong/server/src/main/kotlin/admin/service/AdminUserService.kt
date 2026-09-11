package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.AdminRedisClient
import com.dentalclinic.admin.auth.PasswordEncoder
import com.dentalclinic.admin.dto.response.RoleSimple
import com.dentalclinic.admin.dto.response.UserListItem
import com.dentalclinic.admin.entity.AdminRoles
import com.dentalclinic.admin.entity.AdminUserRoles
import com.dentalclinic.admin.entity.AdminUsers
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.toDisplayString
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*

class AdminUserService : BaseService() {

    suspend fun listUsers(page: Int, pageSize: Int, keyword: String?, roleId: Long?): Pair<List<UserListItem>, Long> =
        dbQuery {
            var query = AdminUsers.selectAll()

            if (!keyword.isNullOrBlank()) {
                query = query.andWhere {
                    (AdminUsers.username like "%$keyword%") or (AdminUsers.displayName like "%$keyword%")
                }
            }

            val total = query.count()

            val rows = query
                .orderBy(AdminUsers.id, SortOrder.DESC)
                .limit(pageSize).offset(((page - 1) * pageSize).toLong())
                .toList()

            val users = rows.map { row ->
                val userId = row[AdminUsers.id]
                UserListItem(
                    id = userId,
                    username = row[AdminUsers.username],
                    displayName = row[AdminUsers.displayName],
                    email = row[AdminUsers.email],
                    status = row[AdminUsers.status],
                    roles = getUserRoles(userId),
                    lastLoginAt = row[AdminUsers.lastLoginAt].toDisplayString(),
                    createdAt = row[AdminUsers.createdAt].toDisplayString() ?: ""
                )
            }.let { list ->
                if (roleId != null) {
                    val userIdsWithRole = AdminUserRoles.selectAll()
                        .where { AdminUserRoles.roleId eq roleId }
                        .toList()
                        .map { it[AdminUserRoles.userId] }
                        .toSet()
                    list.filter { it.id in userIdsWithRole }
                } else list
            }

            users to total
        }

    suspend fun getUser(id: Long): UserListItem = dbQuery {
        val row = AdminUsers.selectAll().where { AdminUsers.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("用户不存在")
        UserListItem(
            id = row[AdminUsers.id],
            username = row[AdminUsers.username],
            displayName = row[AdminUsers.displayName],
            email = row[AdminUsers.email],
            status = row[AdminUsers.status],
            roles = getUserRoles(id),
            lastLoginAt = row[AdminUsers.lastLoginAt].toDisplayString(),
            createdAt = row[AdminUsers.createdAt].toDisplayString() ?: ""
        )
    }

    suspend fun createUser(
        username: String,
        password: String,
        displayName: String,
        email: String?,
        roleIds: List<Long>
    ): Long = dbQuery {
        // 唯一性检查
        val exists = AdminUsers.selectAll().where { AdminUsers.username eq username }.count() > 0
        if (exists) throw ConflictException("用户名已存在: $username")

        if (roleIds.isEmpty()) throw ValidationException("至少需要分配一个角色", mapOf("roleIds" to "不能为空"))

        val userId = AdminUsers.insert {
            it[AdminUsers.username] = username
            it[passwordHash] = PasswordEncoder.hash(password)
            it[AdminUsers.displayName] = displayName
            it[AdminUsers.email] = email
        }[AdminUsers.id]

        AdminUserRoles.batchInsert(roleIds, body = { rId ->
            this[AdminUserRoles.userId] = userId
            this[AdminUserRoles.roleId] = rId
        })

        userId
    }

    suspend fun updateUser(id: Long, displayName: String?, email: String?, roleIds: List<Long>?) = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("用户不存在")

        AdminUsers.update({ AdminUsers.id eq id }) {
            if (displayName != null) it[AdminUsers.displayName] = displayName
            if (email != null) it[AdminUsers.email] = email
        }

        if (roleIds != null) {
            AdminUserRoles.deleteWhere { userId eq id }
            if (roleIds.isNotEmpty()) {
                AdminUserRoles.batchInsert(roleIds, body = { rId ->
                    this[AdminUserRoles.userId] = id
                    this[AdminUserRoles.roleId] = rId
                })
            }
        }
    }

    suspend fun updateStatus(id: Long, status: Int) = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("用户不存在")

        AdminUsers.update({ AdminUsers.id eq id }) {
            it[AdminUsers.status] = status
        }
    }

    suspend fun resetPassword(id: Long, newPassword: String) = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("用户不存在")

        AdminUsers.update({ AdminUsers.id eq id }) {
            it[passwordHash] = PasswordEncoder.hash(newPassword)
        }

        // 使所有 Refresh Token 失效
        AdminRedisClient.deleteAllRefreshTokensForUser(id)
    }

    suspend fun deleteUser(id: Long) = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.toList().singleOrNull()
            ?: throw NotFoundException("用户不存在")

        AdminUserRoles.deleteWhere { userId eq id }
        AdminUsers.deleteWhere { AdminUsers.id eq id }
        AdminRedisClient.deleteAllRefreshTokensForUser(id)
    }

    private suspend fun getUserRoles(userId: Long): List<RoleSimple> {
        return AdminUserRoles
            .join(AdminRoles, JoinType.INNER, AdminUserRoles.roleId, AdminRoles.id)
            .selectAll().where { AdminUserRoles.userId eq userId }
            .toList()
            .map {
                RoleSimple(
                    id = it[AdminRoles.id],
                    roleCode = it[AdminRoles.roleCode],
                    roleName = it[AdminRoles.roleName]
                )
            }
    }
}
