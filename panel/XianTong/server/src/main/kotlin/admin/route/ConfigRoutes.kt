package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.request.CreateNotificationRuleRequest
import com.dentalclinic.admin.dto.request.UpdateConfigRequest
import com.dentalclinic.admin.dto.response.ConfigCategory
import com.dentalclinic.admin.dto.response.NotificationHistoryItem
import com.dentalclinic.admin.dto.response.NotificationRuleItem
import com.dentalclinic.admin.dto.response.SystemConfigItem
import com.dentalclinic.admin.entity.AdminNotificationHistories
import com.dentalclinic.admin.entity.AdminNotificationRules
import com.dentalclinic.admin.entity.AdminSystemConfigs
import com.dentalclinic.admin.util.*
import com.dentalclinic.common.config.DBConfig
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update


fun Route.configRoutes() {

    route("/config") {
        authenticated {

            requirePermission("config:view") {
                // GET /api/admin/config
                get {
                    val configs = suspendTransaction(DBConfig.database) {
                        AdminSystemConfigs.selectAll()
                            .orderBy(
                                AdminSystemConfigs.category to SortOrder.ASC,
                                AdminSystemConfigs.id to SortOrder.ASC
                            )
                            .map { row ->
                                SystemConfigItem(
                                    id = row[AdminSystemConfigs.id],
                                    configKey = row[AdminSystemConfigs.configKey],
                                    configValue = if (row[AdminSystemConfigs.isSensitive] == 1)
                                        maskSensitiveValue(row[AdminSystemConfigs.configValue])
                                    else row[AdminSystemConfigs.configValue],
                                    configType = row[AdminSystemConfigs.configType],
                                    category = row[AdminSystemConfigs.category],
                                    description = row[AdminSystemConfigs.description],
                                    isSensitive = row[AdminSystemConfigs.isSensitive],
                                    reloadType = row[AdminSystemConfigs.reloadType],
                                    minValue = row[AdminSystemConfigs.minValue],
                                    maxValue = row[AdminSystemConfigs.maxValue],
                                    updatedBy = row[AdminSystemConfigs.updatedBy],
                                    updatedAt = row[AdminSystemConfigs.updatedAt].toDisplayString() ?: ""
                                )
                            }.toList()
                            .groupBy { it.category }
                            .map { (cat, items) -> ConfigCategory(category = cat, items = items) }
                    }
                    call.ok(configs)
                }

                // GET /api/admin/config/notifications
                get("/notifications") {
                    val rules = suspendTransaction(DBConfig.database) {
                        AdminNotificationRules.selectAll().map { row ->
                            NotificationRuleItem(
                                id = row[AdminNotificationRules.id],
                                ruleName = row[AdminNotificationRules.ruleName],
                                conditionType = row[AdminNotificationRules.conditionType],
                                threshold = row[AdminNotificationRules.threshold],
                                webhookUrl = row[AdminNotificationRules.webhookUrl],
                                isEnabled = row[AdminNotificationRules.isEnabled],
                                createdAt = row[AdminNotificationRules.createdAt].toDisplayString() ?: ""
                            )
                        }.toList()
                    }
                    call.ok(rules)
                }

                // GET /api/admin/config/notifications/history
                get("/notifications/history") {
                    val pq = PageQuery.from(call)
                    val (items, total) = suspendTransaction(DBConfig.database) {
                        val query = AdminNotificationHistories.selectAll()
                        val t = query.count()
                        val list = query
                            .orderBy(AdminNotificationHistories.triggeredAt, SortOrder.DESC)
                            .limit(pq.pageSize).offset(pq.offset)
                            .map { row ->
                                NotificationHistoryItem(
                                    id = row[AdminNotificationHistories.id],
                                    ruleId = row[AdminNotificationHistories.ruleId],
                                    ruleName = row[AdminNotificationHistories.ruleName],
                                    messageContent = row[AdminNotificationHistories.messageContent],
                                    deliveryStatus = row[AdminNotificationHistories.deliveryStatus],
                                    errorMessage = row[AdminNotificationHistories.errorMessage],
                                    triggeredAt = row[AdminNotificationHistories.triggeredAt].toDisplayString() ?: ""
                                )
                            }.toList()
                        list to t
                    }
                    call.okPage(items, total, pq.page, pq.pageSize)
                }
            }

            requirePermission("config:update") {
                // PUT /api/admin/config/{key}
                put("/{key}") {
                    val key = call.parameters["key"]
                        ?: throw ValidationException("缺少配置键")
                    val req = call.receive<UpdateConfigRequest>()
                    val principal = call.adminPrincipal()

                    suspendTransaction(DBConfig.database) {
                        val config = AdminSystemConfigs.selectAll()
                            .where { AdminSystemConfigs.configKey eq key }
                            .singleOrNull() ?: throw NotFoundException("配置不存在: $key")

                        // 范围校验
                        val minVal = config[AdminSystemConfigs.minValue]
                        val maxVal = config[AdminSystemConfigs.maxValue]
                        if (config[AdminSystemConfigs.configType] == "int") {
                            val intVal = req.value.toIntOrNull()
                                ?: throw ValidationException("配置值必须为整数")
                            if (minVal != null && intVal < minVal)
                                throw ValidationException("配置值不能小于 $minVal")
                            if (maxVal != null && intVal > maxVal)
                                throw ValidationException("配置值不能大于 $maxVal")
                        }

                        AdminSystemConfigs.update({ AdminSystemConfigs.configKey eq key }) {
                            it[configValue] = req.value
                            it[updatedBy] = principal.username
                        }
                    }
                    call.ok<Unit>(message = "配置更新成功")
                }
            }

            requirePermission("config:create") {
                // POST /api/admin/config/notifications
                post("/notifications") {
                    val req = call.receive<CreateNotificationRuleRequest>()
                    suspendTransaction(DBConfig.database) {
                        AdminNotificationRules.insert {
                            it[ruleName] = req.ruleName
                            it[conditionType] = req.conditionType
                            it[threshold] = req.threshold
                            it[webhookUrl] = req.webhookUrl
                            it[isEnabled] = req.isEnabled
                        }
                    }
                    call.ok<Unit>(message = "通知规则创建成功")
                }
            }

            requirePermission("config:delete") {
                // DELETE /api/admin/config/notifications/{id}
                delete("/notifications/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的规则 ID")
                    suspendTransaction(DBConfig.database) {
                        AdminNotificationRules.deleteWhere { AdminNotificationRules.id eq id }
                    }
                    call.ok<Unit>(message = "通知规则已删除")
                }
            }
        }
    }
}
