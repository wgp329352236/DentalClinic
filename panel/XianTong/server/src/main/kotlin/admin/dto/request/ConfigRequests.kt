package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateConfigRequest(
    val value: String
)

@Serializable
data class UpdateConcurrencyRequest(
    val pullPoolSize: Int? = null,
    val pushPoolSize: Int? = null,
    val lowerPoolSize: Int? = null
)

@Serializable
data class CreateNotificationRuleRequest(
    val ruleName: String,
    val conditionType: String, // task_failure, dlq_overflow, missed_schedule
    val threshold: Int,
    val webhookUrl: String,
    val isEnabled: Int = 1
)

@Serializable
data class UpdateNotificationRuleRequest(
    val ruleName: String? = null,
    val conditionType: String? = null,
    val threshold: Int? = null,
    val webhookUrl: String? = null,
    val isEnabled: Int? = null
)
