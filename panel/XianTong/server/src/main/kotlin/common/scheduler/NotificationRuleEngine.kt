package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.AdminNotificationHistories
import com.dentalclinic.admin.entity.AdminNotificationRules
import com.dentalclinic.common.BaseService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 通知规则执行引擎（P1-11）。
 *
 * 定期检查启用的通知规则，评估条件并发送 webhook 通知，记录执行历史。
 */
object NotificationRuleEngine : BaseService() {

    private val logger = LoggerFactory.getLogger("NotificationRuleEngine")
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var running = false

    /**
     * 启动规则执行引擎（每小时执行一次）。
     */
    fun start() {
        if (running) return
        running = true

        scope.launch {
            while (isActive && running) {
                try {
                    executeRules()
                } catch (e: Exception) {
                    logger.error("[NotificationRuleEngine] 执行规则失败", e)
                }
                delay(3600_000) // 1小时
            }
        }
        logger.info("[NotificationRuleEngine] 通知规则执行引擎已启动")
    }

    /**
     * 停止规则执行引擎。
     */
    fun stop() {
        running = false
    }

    /**
     * 执行所有启用的通知规则。
     */
    private suspend fun executeRules() = dbQuery {
        val rules = AdminNotificationRules.selectAll()
            .where { AdminNotificationRules.isEnabled eq 1 }
            .toList()

        for (rule in rules) {
            try {
                evaluateAndExecuteRule(rule)
            } catch (e: Exception) {
                logger.error("[NotificationRuleEngine] 执行规则 ${rule[AdminNotificationRules.id]} 失败", e)
                recordHistory(
                    ruleId = rule[AdminNotificationRules.id],
                    ruleName = rule[AdminNotificationRules.ruleName],
                    content = "规则执行异常: ${e.message}",
                    status = "failed",
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 评估并执行单个规则。
     */
    private suspend fun evaluateAndExecuteRule(rule: org.jetbrains.exposed.v1.core.ResultRow) {
        val ruleId = rule[AdminNotificationRules.id]
        val ruleName = rule[AdminNotificationRules.ruleName]
        val conditionType = rule[AdminNotificationRules.conditionType]
        val threshold = rule[AdminNotificationRules.threshold]
        val webhookUrl = rule[AdminNotificationRules.webhookUrl]

        // 评估条件（示例：基于预约数量、消费金额等）
        val conditionMet = evaluateCondition(conditionType, threshold)

        if (conditionMet) {
            val message = buildMessage(ruleName, conditionType, threshold)

            // 发送 webhook
            val success = sendWebhook(webhookUrl, message)

            recordHistory(
                ruleId = ruleId,
                ruleName = ruleName,
                content = message,
                status = if (success) "success" else "failed",
                errorMessage = if (!success) "Webhook 发送失败" else null
            )

            logger.info("[NotificationRuleEngine] 规则 $ruleName 执行${if (success) "成功" else "失败"}")
        }
    }

    /**
     * 评估条件（示例实现）。
     *
     * 实际生产环境应根据 conditionType 查询业务数据并评估。
     */
    private suspend fun evaluateCondition(conditionType: String, threshold: Int): Boolean {
        // 示例：基于预约数量
        // 实际应根据 conditionType 查询不同业务表
        return when (conditionType) {
            "appointment_count" -> {
                // 示例：今日预约数超过阈值
                // val todayCount = Appointments.selectAll().where { ... }.count()
                // todayCount > threshold
                false // 占位实现
            }
            "consumption_amount" -> {
                // 示例：今日消费金额超过阈值
                false // 占位实现
            }
            else -> {
                logger.warn("[NotificationRuleEngine] 未知的条件类型: $conditionType")
                false
            }
        }
    }

    /**
     * 构建通知消息。
     */
    private fun buildMessage(ruleName: String, conditionType: String, threshold: Int): String {
        return "【通知规则触发】规则名称: $ruleName, 条件类型: $conditionType, 阈值: $threshold, 触发时间: ${LocalDateTime.now()}"
    }

    /**
     * 发送 webhook 通知。
     */
    private suspend fun sendWebhook(url: String, message: String): Boolean {
        return try {
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody("""{"message": "$message", "timestamp": "${LocalDateTime.now()}"}""")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.error("[NotificationRuleEngine] Webhook 发送失败: ${e.message}")
            false
        }
    }

    /**
     * 记录通知执行历史。
     */
    private suspend fun recordHistory(
        ruleId: Long,
        ruleName: String,
        content: String,
        status: String,
        errorMessage: String?
    ) = dbQuery {
        AdminNotificationHistories.insert {
            it[AdminNotificationHistories.ruleId] = ruleId
            it[AdminNotificationHistories.ruleName] = ruleName
            it[AdminNotificationHistories.messageContent] = content
            it[AdminNotificationHistories.deliveryStatus] = status
            it[AdminNotificationHistories.errorMessage] = errorMessage
            it[AdminNotificationHistories.triggeredAt] = LocalDateTime.now()
        }
    }
}
