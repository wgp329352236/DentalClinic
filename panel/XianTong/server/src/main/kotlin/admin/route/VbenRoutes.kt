package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ok
import com.dentalclinic.common.config.DBConfig
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Vben Admin v5.7.0 适配接口
 * 这些接口格式严格匹配 Vben Admin 前端期望
 */
fun Route.vbenRoutes() {

    // GET /api/admin/user/info — Vben Admin UserInfo 格式
    route("/user") {
        authenticated {
            get("/info") {
                val principal = call.adminPrincipal()
                val userId = principal.userId

                val userInfo = suspendTransaction(DBConfig.database) {
                    val user = AdminUsers.selectAll().where { AdminUsers.id eq userId }.single()
                    val roles = AdminUserRoles
                        .join(AdminRoles, JoinType.INNER, AdminUserRoles.roleId, AdminRoles.id)
                        .selectAll().where { AdminUserRoles.userId eq userId }
                        .toList()
                        .map { it[AdminRoles.roleCode] }

                    // 门店数据范围：拥有 clinic:data:all 视为全店；否则返回授权门店 ID 列表。
                    // 前端据此决定门店筛选器是否必选/默认值，真正的隔离仍以后端各列表接口的 scope 过滤为准。
                    val allClinics = principal.permissions.contains("clinic:data:all")
                    val clinicIds = if (allClinics) {
                        emptyList()
                    } else {
                        AdminUserClinics.selectAll()
                            .where { AdminUserClinics.userId eq userId }
                            .toList()
                            .map { it[AdminUserClinics.clinicId] }
                    }

                    VbenUserInfo(
                        userId = userId,
                        username = user[AdminUsers.username],
                        realName = user[AdminUsers.displayName],
                        avatar = user[AdminUsers.avatar] ?: "",
                        desc = roles.firstOrNull() ?: "",
                        homePath = "/dashboard",
                        roles = roles,
                        allClinics = allClinics,
                        clinicIds = clinicIds,
                        token = "" // 前端会从 store 中获取
                    )
                }

                call.ok(userInfo)
            }
        }
    }

    // GET /api/admin/auth/codes — 权限码数组
    route("/auth") {
        authenticated {
            get("/codes") {
                val principal = call.adminPrincipal()
                call.ok(principal.permissions)
            }
        }
    }

    // GET /api/admin/menu/all — VbenRouteRecord 格式菜单
    route("/menu") {
        authenticated {
            get("/all") {
                val principal = call.adminPrincipal()
                val userId = principal.userId

                val menus = suspendTransaction(DBConfig.database) {
                    // 获取用户可访问的菜单
                    val menuRows = AdminUserRoles
                        .join(AdminRoleMenus, JoinType.INNER, AdminUserRoles.roleId, AdminRoleMenus.roleId)
                        .join(AdminMenus, JoinType.INNER, AdminRoleMenus.menuId, AdminMenus.id)
                        .selectAll()
                        .where { AdminUserRoles.userId eq userId }
                        .withDistinct()
                        .orderBy(AdminMenus.sortOrder)
                        .toList()

                    // 转换为 VbenRouteRecord 格式
                    val allMenus = menuRows.map { row ->
                        val routePath = row[AdminMenus.routePath] ?: ""
                        val hasParam = routePath.contains(":")
                        VbenMenuNode(
                            id = row[AdminMenus.id],
                            parentId = row[AdminMenus.parentId],
                            name = generateRouteName(routePath),
                            path = routePath,
                            component = row[AdminMenus.component],
                            redirect = null,
                            meta = VbenRouteMeta(
                                title = row[AdminMenus.menuName],
                                icon = row[AdminMenus.icon],
                                order = row[AdminMenus.sortOrder],
                                hideInMenu = hasParam || row[AdminMenus.isVisible] == 0,
                                keepAlive = row[AdminMenus.isCache] == 1,
                                activePath = if (hasParam) routePath.substringBeforeLast("/") else null
                            )
                        )
                    }.distinctBy { it.id }

                    buildVbenMenuTree(allMenus, null)
                }

                call.ok(menus)
            }
        }
    }
}

// ============================================================
// Vben Admin 数据结构
// ============================================================

@Serializable
data class VbenUserInfo(
    val userId: Long,
    val username: String,
    val realName: String,
    val avatar: String,
    val desc: String,
    val homePath: String,
    val roles: List<String>,
    // 是否拥有全部门店数据权限；false 时 clinicIds 为授权门店集合（可能为空=无业务数据）
    val allClinics: Boolean = false,
    val clinicIds: List<Long> = emptyList(),
    val token: String
)

@Serializable
data class VbenRouteRecord(
    val name: String,
    val path: String,
    val component: String? = null,
    val redirect: String? = null,
    val meta: VbenRouteMeta,
    val children: List<VbenRouteRecord>? = null
)

@Serializable
data class VbenRouteMeta(
    val title: String,
    val icon: String? = null,
    val order: Int? = null,
    val hideInMenu: Boolean? = null,
    val activePath: String? = null,
    val affixTab: Boolean? = null,
    val keepAlive: Boolean? = null
)

// 内部用于构建树
private data class VbenMenuNode(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val path: String,
    val component: String?,
    val redirect: String?,
    val meta: VbenRouteMeta
)

private fun buildVbenMenuTree(nodes: List<VbenMenuNode>, parentId: Long?): List<VbenRouteRecord> {
    val children = nodes.filter { it.parentId == parentId }
    return children.map { node ->
        val subChildren = buildVbenMenuTree(nodes, node.id)
        VbenRouteRecord(
            name = node.name,
            path = node.path,
            component = if (subChildren.isEmpty()) node.component else null,
            redirect = if (subChildren.isNotEmpty()) subChildren.firstOrNull()?.path else null,
            meta = node.meta,
            children = subChildren.takeIf { it.isNotEmpty() }
        )
    }
}

private fun generateRouteName(path: String): String {
    // /dashboard/analytics → DashboardAnalytics
    return path.split("/")
        .filter { it.isNotBlank() && !it.startsWith(":") }
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { "Root" }
}
