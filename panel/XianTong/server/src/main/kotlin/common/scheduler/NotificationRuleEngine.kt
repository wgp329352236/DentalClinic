package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.AdminNotificationHistories
import com.dentalclinic.admin.entity.AdminNotificationRules
import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.common.BaseService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
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

        val evaluation = evaluateCondition(conditionType, threshold) ?: run {
            logger.warn("[NotificationRuleEngine] 忽略未知条件类型: $conditionType（规则 $ruleName）")
            return
        }

        if (evaluation.met) {
            // 同一规则当天成功通知后不再重复推送；失败记录允许下一轮重试。
            val dayStart = evaluation.statisticDate.atStartOfDay()
            val alreadySent = AdminNotificationHistories.selectAll()
                .where {
                    (AdminNotificationHistories.ruleId eq ruleId) and
                        (AdminNotificationHistories.deliveryStatus eq "success") and
                        (AdminNotificationHistories.triggeredAt greaterEq dayStart) and
                        (AdminNotificationHistories.triggeredAt less dayStart.plusDays(1))
                }
                .count() > 0
            if (alreadySent) {
                logger.debug("[NotificationRuleEngine] 规则 $ruleName 今日已成功通知，跳过")
                return
            }

            val message = buildMessage(ruleName, conditionType, threshold, evaluation)

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
     * 评估条件。未知条件返回 null，由调用方仅告警、不触发。
     */
    private suspend fun evaluateCondition(conditionType: String, threshold: Int): ConditionEvaluation? {
        val today = LocalDate.now()
        return when (conditionType) {
            "appointment_count" -> {
                val count = Appointments.selectAll()
                    .where {
                        (Appointments.appointmentDate eq today) and
                            (Appointments.status neq "cancelled") and
                            (Appointments.status neq "no_show")
                    }
                    .count()
                ConditionEvaluation(
                    met = count > threshold,
                    actualValue = BigDecimal(count),
                    unit = "单",
                    statisticDate = today
                )
            }
            "consumption_amount" -> {
                val dayStart = today.atStartOfDay()
                val total = ConsumptionRecords.selectAll()
                    .where {
                        (ConsumptionRecords.status eq "success") and
                            (ConsumptionRecords.paymentStatus eq "success") and
                            (ConsumptionRecords.createdAt greaterEq dayStart) and
                            (ConsumptionRecords.createdAt less dayStart.plusDays(1))
                    }
                    .toList()
                    .fold(BigDecimal.ZERO) { sum, row ->
                        sum.add(row[ConsumptionRecords.amount])
                    }
                ConditionEvaluation(
                    met = total > BigDecimal(threshold),
                    actualValue = total,
                    unit = "元",
                    statisticDate = today
                )
            }
            else -> null
        }
    }

    /**
     * 构建通知消息。
     */
    private fun buildMessage(
        ruleName: String,
        conditionType: String,
        threshold: Int,
        evaluation: ConditionEvaluation
    ): String {
        val conditionLabel = when (conditionType) {
            "appointment_count" -> "今日预约数"
            "consumption_amount" -> "今日消费金额"
            else -> conditionType
        }
        val actual = evaluation.actualValue.stripTrailingZeros().toPlainString()
        val thresholdText = BigDecimal(threshold).stripTrailingZeros().toPlainString()
        return "【通知规则触发】规则名称: $ruleName, " +
            "$conditionLabel: $actual${evaluation.unit}, 阈值: $thresholdText${evaluation.unit}, " +
            "统计日期: ${evaluation.statisticDate}, 触发时间: ${LocalDateTime.now()}"
    }

    /**
     * 发送 webhook 通知。
     */
    private suspend fun sendWebhook(url: String, message: String): Boolean {
        return try {
            val body = buildJsonObject {
                put("message", message)
                put("timestamp", LocalDateTime.now().toString())
            }
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
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

    private data class ConditionEvaluation(
        val met: Boolean,
        val actualValue: BigDecimal,
        val unit: String,
        val statisticDate: LocalDate
    )
}
