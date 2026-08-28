package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class SystemConfigItem(
    val id: Long,
    val configKey: String,
    val configValue: String, // 敏感值已脱敏
    val configType: String,
    val category: String,
    val description: String? = null,
    val isSensitive: Int,
    val reloadType: String,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val updatedBy: String? = null,
    val updatedAt: String
)

@Serializable
data class ConfigCategory(
    val category: String,
    val items: List<SystemConfigItem>
)

@Serializable
data class NotificationRuleItem(
    val id: Long,
    val ruleName: String,
    val conditionType: String,
    val threshold: Int,
    val webhookUrl: String,
    val isEnabled: Int,
    val createdAt: String
)

@Serializable
data class NotificationHistoryItem(
    val id: Long,
    val ruleId: Long,
    val ruleName: String,
    val messageContent: String,
    val deliveryStatus: String,
    val errorMessage: String? = null,
    val triggeredAt: String
)

@Serializable
data class AuditLogItem(
    val id: Long,
    val userId: Long? = null,
    val username: String,
    val operationType: String,
    val targetResource: String,
    val targetId: String? = null,
    val requestParams: String? = null,
    val responseCode: Int? = null,
    val ipAddress: String? = null,
    val createdAt: String
)

@Serializable
data class AuditLogDetail(
    val id: Long,
    val userId: Long? = null,
    val username: String,
    val operationType: String,
    val targetResource: String,
    val targetId: String? = null,
    val requestParams: String? = null,
    val responseCode: Int? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: String
)
