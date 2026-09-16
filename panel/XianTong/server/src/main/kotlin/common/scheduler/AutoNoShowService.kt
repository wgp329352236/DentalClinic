package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * 自动爽约判定任务（需求 3.2：扫码签到 + 爽约约束）。
 *
 * 每小时 :15 扫描「预约日期早于今天」且仍处于 pending/confirmed 的预约，
 * 置为 no_show 并累加 no_show_count。用户近 30 天累计 3 次爽约后，
 * 创建预约接口会直接拒绝（见 AppointmentService 黑名单）。
 *
 * 只按日期严格小于今天判定：当天已过时但患者可能迟到或在前台人工补签到，
 * 当天不自动判爽，把决定权留给门店，避免误伤。
 */
object AutoNoShowService {

    private val logger = LoggerFactory.getLogger("AutoNoShow")

    private val ACTIVE_STATUSES = listOf("pending", "confirmed")

    private data class NoShow(val appointmentId: Long, val userId: Long)

    fun start() {
        val scheduler = newDaemonScheduledExecutor("auto-noshow-thread")
        scheduler.scheduleHourly("AutoNoShow", minutePast = 15) { sweep() }
        logger.info("[AutoNoShow]: 自动爽约判定任务已调度（每小时 :15）")
    }

    private suspend fun sweep() {
        val today = LocalDate.now()
        val due = suspendTransaction(DBConfig.database) {
            val rows = Appointments.selectAll()
                .where {
                    (Appointments.appointmentDate less today) and
                        (Appointments.status inList ACTIVE_STATUSES)
                }
                .toList()
            if (rows.isEmpty()) return@suspendTransaction emptyList()

            val ids = rows.map { it[Appointments.id] }
            Appointments.update({
                (Appointments.id inList ids) and (Appointments.status inList ACTIVE_STATUSES)
            }) {
                it[Appointments.status] = "no_show"
                it[Appointments.noShowCount] = Appointments.noShowCount + 1
            }
            rows.map { NoShow(it[Appointments.id], it[Appointments.userId]) }
        }

        if (due.isEmpty()) return

        // 站内信通知（爽约发生时无法事前订阅对应模板，不发订阅消息）
        due.forEach { item ->
            try {
                ScheduledNotifier.notify(
                    userId = item.userId,
                    templateKey = "appointment_no_show",
                    title = "预约爽约提醒",
                    content = "您的预约（订单号 ${item.appointmentId}）因未到店已记为爽约。" +
                        "近 30 天内累计 3 次爽约将暂停在线预约 30 天，如有疑问请联系门店。",
                    subscribeData = emptyMap(),
                    msgType = "system",
                    relatedId = item.appointmentId
                )
            } catch (e: Exception) {
                logger.warn("[AutoNoShow]: 爽约通知失败 appointmentId={}: {}", item.appointmentId, e.message)
            }
        }
        logger.info("[AutoNoShow]: 自动判定爽约 {} 单", due.size)
    }
}
