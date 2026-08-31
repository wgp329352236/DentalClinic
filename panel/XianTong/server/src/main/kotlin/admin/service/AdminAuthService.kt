package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.AdminRedisClient
import com.dentalclinic.admin.auth.JwtConfig
import com.dentalclinic.admin.auth.PasswordEncoder
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.AuthenticationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class AdminAuthService : BaseService() {

    private val logger = LoggerFactory.getLogger(AdminAuthService::class.java)

    /**
     * 用户登录
     */
    suspend fun login(username: String, password: String): LoginResponse {
        // 1. 检查账户锁定
        if (AdminRedisClient.isAccountLocked(username)) {
            throw AuthenticationException("账户已锁定，请30分钟后重试")
        }

        // 2. 查询用户
        val user = dbQuery {
            AdminUsers.selectAll().where { AdminUsers.username eq username }.toList().singleOrNull()
        } ?: run {
            AdminRedisClient.incrementFailCount(username)
            throw AuthenticationException("用户名或密码错误")
        }

        // 3. 检查用户状态
        if (user[AdminUsers.status] == 0) {
            throw AuthenticationException("账户已被禁用")
        }

        // 4. 验证密码
        if (!PasswordEncoder.verify(password, user[AdminUsers.passwordHash])) {
            AdminRedisClient.incrementFailCount(username)
            throw AuthenticationException("用户名或密码错误")
        }

        // 5. 登录成功，清除失败计数
        AdminRedisClient.clearFailCount(username)

        val userId = user[AdminUsers.id]

        // 6. 查询角色和权限
        val roles = dbQuery { getUserRoles(userId) }
        val permissions = dbQuery { getUserPermissions(userId) }
        val menus = dbQuery { getUserMenus(userId) }

        // 7. 生成 Token
        val accessToken = JwtConfig.generateAccessToken(userId, username, permissions)
        val (tokenId, refreshToken) = JwtConfig.generateRefreshToken(userId)

        // 8. 存储 Refresh Token 到 Redis
        AdminRedisClient.storeRefreshToken(tokenId, userId, JwtConfig.REFRESH_TOKEN_TTL_SECONDS)

        // 9. 更新最后登录时间
        dbQuery {
            AdminUsers.update({ AdminUsers.id eq userId }) {
                it[lastLoginAt] = LocalDateTime.now()
            }
        }

        logger.info("[AdminAuth]: 用户 $username 登录成功")

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userInfo = UserInfoResponse(
                id = userId,
                username = username,
                displayName = user[AdminUsers.displayName],
                email = user[AdminUsers.email],
                avatar = user[AdminUsers.avatar],
                roles = roles
            ),
            menus = buildMenuTree(menus),
            permissions = permissions
        )
    }

    /**
     * 刷新 Token
     */
    suspend fun refresh(refreshToken: String): RefreshResponse {
        val jwt = JwtConfig.verifyToken(refreshToken)
            ?: throw AuthenticationException("刷新令牌已失效")

        if (JwtConfig.getTokenType(jwt) != "refresh") {
            throw AuthenticationException("无效的令牌类型")
        }

        val tokenId = JwtConfig.getTokenId(jwt)
        val userId = AdminRedisClient.getRefreshTokenUserId(tokenId)
            ?: throw AuthenticationException("刷新令牌已失效")

        // 查询用户当前权限
        val user = dbQuery {
            AdminUsers.selectAll().where { AdminUsers.id eq userId }.toList().singleOrNull()
        } ?: throw AuthenticationException("用户不存在")

        val username = user[AdminUsers.username]
        val permissions = dbQuery { getUserPermissions(userId) }

        val accessToken = JwtConfig.generateAccessToken(userId, username, permissions)
        return RefreshResponse(accessToken = accessToken)
    }

    /**
     * 修改密码
     */
    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        val user = dbQuery {
            AdminUsers.selectAll().where { AdminUsers.id eq userId }.toList().singleOrNull()
        } ?: throw AuthenticationException("用户不存在")

        if (!PasswordEncoder.verify(oldPassword, user[AdminUsers.passwordHash])) {
            throw AuthenticationException("旧密码错误")
        }

        dbQuery {
            AdminUsers.update({ AdminUsers.id eq userId }) {
                it[passwordHash] = PasswordEncoder.hash(newPassword)
            }
        }

        logger.info("[AdminAuth]: 用户 ${user[AdminUsers.username]} 修改了密码")
    }


    /**
     * 退出登录
     */
    fun logout(refreshToken: String) {
        val jwt = JwtConfig.verifyToken(refreshToken) ?: return
        if (JwtConfig.getTokenType(jwt) == "refresh") {
            val tokenId = JwtConfig.getTokenId(jwt)
            AdminRedisClient.deleteRefreshToken(tokenId)
        }
    }

    /**
     * 获取用户信息（含菜单和权限）
     */
    suspend fun getProfile(userId: Long): LoginResponse {
        val user = dbQuery {
            AdminUsers.selectAll().where { AdminUsers.id eq userId }.toList().singleOrNull()
        } ?: throw AuthenticationException("用户不存在")

        val roles = dbQuery { getUserRoles(userId) }
        val permissions = dbQuery { getUserPermissions(userId) }
        val menus = dbQuery { getUserMenus(userId) }

        return LoginResponse(
            accessToken = "",
            refreshToken = "",
            userInfo = UserInfoResponse(
                id = userId,
                username = user[AdminUsers.username],
                displayName = user[AdminUsers.displayName],
                email = user[AdminUsers.email],
                avatar = user[AdminUsers.avatar],
                roles = roles
            ),
            menus = buildMenuTree(menus),
            permissions = permissions
        )
    }

    // ============================================================
    // Private helpers
    // ============================================================

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

    private suspend fun getUserPermissions(userId: Long): List<String> {
        return AdminUserRoles
            .join(AdminRolePermissions, JoinType.INNER, AdminUserRoles.roleId, AdminRolePermissions.roleId)
            .join(AdminPermissions, JoinType.INNER, AdminRolePermissions.permissionId, AdminPermissions.id)
            .select(AdminPermissions.permissionCode)
            .where { AdminUserRoles.userId eq userId }
            .withDistinct()
            .map { it[AdminPermissions.permissionCode] }
            .toList()
    }

    private suspend fun getUserMenus(userId: Long): List<ResultRow> {
        return AdminUserRoles
            .join(AdminRoleMenus, JoinType.INNER, AdminUserRoles.roleId, AdminRoleMenus.roleId)
            .join(AdminMenus, JoinType.INNER, AdminRoleMenus.menuId, AdminMenus.id)
            .selectAll()
            .where { AdminUserRoles.userId eq userId }
            .withDistinct()
            .orderBy(AdminMenus.sortOrder)
            .toList()
    }

    private fun buildMenuTree(rows: List<ResultRow>): List<MenuTreeNode> {
        val allNodes = rows.map { row ->
            MenuTreeNode(
                id = row[AdminMenus.id],
                parentId = row[AdminMenus.parentId],
                menuName = row[AdminMenus.menuName],
                menuType = row[AdminMenus.menuType],
                routePath = row[AdminMenus.routePath],
                component = row[AdminMenus.component],
                permissionCode = row[AdminMenus.permissionCode],
                icon = row[AdminMenus.icon],
                sortOrder = row[AdminMenus.sortOrder],
                isVisible = row[AdminMenus.isVisible],
                isCache = row[AdminMenus.isCache]
            )
        }.distinctBy { it.id }

        val childrenMap = allNodes.groupBy { it.parentId }

        fun buildChildren(parentId: Long?): List<MenuTreeNode> {
            return childrenMap[parentId]?.map { node ->
                node.copy(children = buildChildren(node.id))
            } ?: emptyList()
        }

        return buildChildren(null)
    }
}
