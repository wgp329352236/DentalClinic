package com.dentalclinic.admin.auth

import com.dentalclinic.admin.entity.AdminUserClinics
import com.dentalclinic.common.config.DBConfig
import io.ktor.server.application.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * 管理端账号的门店数据范围（数据隔离口径）。
 *
 * - [all] = true：拥有 `clinic:data:all` 权限，可见全部门店的业务数据；
 * - 否则仅可见 [clinicIds] 中已授权门店的数据（预约/客户/消费/充值流水/看板）；
 * - [clinicIds] 为空表示看不到任何业务数据——默认收敛，而不是默认放开。
 *
 * 账号/角色/菜单等系统管理功能不受此范围限制。
 */
data class ClinicScope(
    val all: Boolean,
    val clinicIds: List<Long>
) {
    /** 非全店权限且未授权任何门店：业务列表/统计应直接返回空结果。 */
    val isEmpty: Boolean get() = !all && clinicIds.isEmpty()

    companion object {
        val ALL = ClinicScope(all = true, clinicIds = emptyList())
    }
}

/** 全部门店数据权限码；需与 DataSeedService.permissionCatalog 中播种的权限码一致。 */
const val CLINIC_DATA_ALL_PERMISSION = "clinic:data:all"

/**
 * 解析当前登录账号的门店数据范围。
 *
 * 每次请求查一次 admin_user_clinic（行数即授权门店数，量级很小）；
 * 若未来账号量/门店量上涨，可改为随登录态缓存进 Redis。
 */
suspend fun ApplicationCall.clinicScope(): ClinicScope {
    val principal = adminPrincipal()
    if (principal.permissions.contains(CLINIC_DATA_ALL_PERMISSION)) {
        return ClinicScope.ALL
    }
    val ids = suspendTransaction(DBConfig.database) {
        AdminUserClinics.selectAll()
            .where { AdminUserClinics.userId eq principal.userId }
            .toList()
            .map { it[AdminUserClinics.clinicId] }
    }
    return ClinicScope(all = false, clinicIds = ids)
}
