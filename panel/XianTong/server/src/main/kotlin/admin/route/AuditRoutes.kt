package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.AuditLogDetail
import com.dentalclinic.admin.dto.response.AuditLogItem
import com.dentalclinic.admin.entity.AdminAuditLogs
import com.dentalclinic.admin.util.*
import com.dentalclinic.config.DBConfig
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun Route.auditRoutes() {

    route("/audit") {
        authenticated {
            requirePermission("audit:view") {

                // GET /api/admin/audit/logs
                get("/logs") {
                    val pq = PageQuery.from(call)
                    val operator = call.parameters["operator"]
                    val operationType = call.parameters["operationType"]
                    val targetResource = call.parameters["targetResource"]

                    val (items, total) = suspendTransaction(DBConfig.database) {
                        var query = AdminAuditLogs.selectAll()

                        if (!operator.isNullOrBlank())
                            query = query.where { AdminAuditLogs.username eq operator }
                        if (!operationType.isNullOrBlank())
                            query = query.andWhere { AdminAuditLogs.operationType eq operationType }
                        if (!targetResource.isNullOrBlank())
                            query = query.andWhere { AdminAuditLogs.targetResource eq targetResource }

                        val t = query.count()
                        val list = query
                            .orderBy(AdminAuditLogs.createdAt, SortOrder.DESC)
                            .limit(pq.pageSize).offset(pq.offset)
                            .toList()
                            .map { row ->
                                AuditLogItem(
                                    id = row[AdminAuditLogs.id],
                                    userId = row[AdminAuditLogs.userId],
                                    username = row[AdminAuditLogs.username],
                                    operationType = row[AdminAuditLogs.operationType],
                                    targetResource = row[AdminAuditLogs.targetResource],
                                    targetId = row[AdminAuditLogs.targetId],
                                    requestParams = row[AdminAuditLogs.requestParams],
                                    responseCode = row[AdminAuditLogs.responseCode],
                                    ipAddress = row[AdminAuditLogs.ipAddress],
                                    createdAt = row[AdminAuditLogs.createdAt].toDisplayString() ?: ""
                                )
                            }
                        list to t
                    }

                    call.okPage(items, total, pq.page, pq.pageSize)
                }

                // GET /api/admin/audit/logs/{id}
                get("/logs/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的日志 ID")

                    val detail = suspendTransaction(DBConfig.database) {
                        AdminAuditLogs.selectAll()
                            .where { AdminAuditLogs.id eq id }
                            .toList().singleOrNull()?.let { row ->
                                AuditLogDetail(
                                    id = row[AdminAuditLogs.id],
                                    userId = row[AdminAuditLogs.userId],
                                    username = row[AdminAuditLogs.username],
                                    operationType = row[AdminAuditLogs.operationType],
                                    targetResource = row[AdminAuditLogs.targetResource],
                                    targetId = row[AdminAuditLogs.targetId],
                                    requestParams = row[AdminAuditLogs.requestParams],
                                    responseCode = row[AdminAuditLogs.responseCode],
                                    ipAddress = row[AdminAuditLogs.ipAddress],
                                    userAgent = row[AdminAuditLogs.userAgent],
                                    createdAt = row[AdminAuditLogs.createdAt].toDisplayString() ?: ""
                                )
                            } ?: throw NotFoundException("审计日志不存在")
                    }
                    call.ok(detail)
                }
            }
        }
    }
}
