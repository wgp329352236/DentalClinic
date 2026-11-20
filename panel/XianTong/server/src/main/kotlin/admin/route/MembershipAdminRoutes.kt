package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.request.CustomerTagUpsertRequest
import com.dentalclinic.admin.dto.request.MemberLevelUpsertRequest
import com.dentalclinic.admin.dto.request.MessageTemplateUpsertRequest
import com.dentalclinic.admin.dto.request.RechargeTierUpsertRequest
import com.dentalclinic.admin.dto.request.SetUserTagsRequest
import com.dentalclinic.admin.service.CustomerTagAdminService
import com.dentalclinic.admin.service.MemberLevelAdminService
import com.dentalclinic.admin.service.MessageTemplateAdminService
import com.dentalclinic.admin.service.RechargeRecordAdminService
import com.dentalclinic.admin.service.RechargeTierAdminService
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

// ==================== 充值档位 ====================

fun Route.rechargeTierRoutes(service: RechargeTierAdminService) {
    route("/recharge-tiers") {
        authenticated {
            requirePermission("recharge_tier:list") {
                get {
                    call.ok(service.listTiers())
                }
            }
            requirePermission("recharge_tier:create") {
                post {
                    val req = call.receive<RechargeTierUpsertRequest>()
                    val id = service.createTier(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("recharge_tier:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的档位ID")
                    val req = call.receive<RechargeTierUpsertRequest>()
                    service.updateTier(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("recharge_tier:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的档位ID")
                    service.deleteTier(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 会员等级 ====================

fun Route.memberLevelRoutes(service: MemberLevelAdminService) {
    route("/member-levels") {
        authenticated {
            requirePermission("member_level:list") {
                get {
                    call.ok(service.listLevels())
                }
            }
            requirePermission("member_level:create") {
                post {
                    val req = call.receive<MemberLevelUpsertRequest>()
                    val id = service.createLevel(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("member_level:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的等级ID")
                    val req = call.receive<MemberLevelUpsertRequest>()
                    service.updateLevel(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("member_level:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的等级ID")
                    service.deleteLevel(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 充值流水 ====================

fun Route.rechargeRecordRoutes(service: RechargeRecordAdminService) {
    route("/recharge-records") {
        authenticated {
            requirePermission("recharge:list") {
                get {
                    val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20)
                        .coerceIn(1, 100)
                    val data = service.listRecords(
                        keyword = call.request.queryParameters["keyword"],
                        clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull(),
                        status = call.request.queryParameters["status"],
                        dateFrom = call.request.queryParameters["dateFrom"],
                        dateTo = call.request.queryParameters["dateTo"],
                        page = page,
                        pageSize = pageSize,
                        scope = call.clinicScope()
                    )
                    call.ok(data)
                }
            }
        }
    }
}

// ==================== 客户标签 ====================

fun Route.customerTagRoutes(service: CustomerTagAdminService) {
    route("/customer-tags") {
        authenticated {
            requirePermission("customer_tag:list") {
                get {
                    call.ok(service.listTags())
                }
            }
            requirePermission("customer_tag:create") {
                post {
                    val req = call.receive<CustomerTagUpsertRequest>()
                    val id = service.createTag(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("customer_tag:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的标签ID")
                    val req = call.receive<CustomerTagUpsertRequest>()
                    service.updateTag(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("customer_tag:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的标签ID")
                    service.deleteTag(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }

            // 查询某客户的标签（打标弹窗回显）
            requirePermission("customer_tag:list") {
                get("/users/{userId}") {
                    val userId = call.parameters["userId"]?.toLongOrNull()
                        ?: throw ValidationException("无效的客户ID")
                    call.ok(service.getUserTags(userId, call.clinicScope()))
                }
            }
            // 全量覆盖客户标签
            requirePermission("customer_tag:update") {
                put("/users/{userId}") {
                    val userId = call.parameters["userId"]?.toLongOrNull()
                        ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<SetUserTagsRequest>()
                    service.setUserTags(userId, req, call.clinicScope())
                    call.ok<Unit>(message = "标签已保存")
                }
            }
        }
    }
}

// ==================== 消息模板 ====================

fun Route.messageTemplateRoutes(service: MessageTemplateAdminService) {
    route("/message-templates") {
        authenticated {
            requirePermission("message_template:list") {
                get {
                    call.ok(service.listTemplates())
                }
            }
            requirePermission("message_template:create") {
                post {
                    val req = call.receive<MessageTemplateUpsertRequest>()
                    val id = service.createTemplate(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("message_template:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的模板ID")
                    val req = call.receive<MessageTemplateUpsertRequest>()
                    service.updateTemplate(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("message_template:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的模板ID")
                    service.deleteTemplate(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
