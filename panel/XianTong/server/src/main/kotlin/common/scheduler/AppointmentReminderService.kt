package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.Doctors
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 预约提醒定时任务（需求 3.6.1）。
 *
 * 每小时整点过 5 分 tick：
 * - 20 点：扫「明天」就诊、未发晚间提醒的 pending/confirmed 预约，发一条后置 remind_evening_sent=1；
 * - 10 点：扫「今天」就诊、未发晨间提醒的，置 remind_morning_sent=1。
 *
 * 幂等：行级标记位 + 只在对应小时 tick，重复 tick / 服务重启都不会重复发送。
 * 先在事务内「认领」（置标记位）再发消息：发送失败（含微信不可达）由
 * [ScheduledNotifier] 降级站内信兜底；极端情况下消息丢失也不会重复轰炸用户。
 */
object AppointmentReminderService {

    private val logger = LoggerFactory.getLogger("AppointmentReminder")

    private const val TPL_EVENING = "appointment_remind_evening"
    private const val TPL_MORNING = "appointment_remind_morning"
    private const val APPOINTMENT_PAGE = "pages/appointments/appointments"

    private val ACTIVE_STATUSES = listOf("pending", "confirmed")
    private val dateTextFormatter = DateTimeFormatter.ofPattern("M月d日")

    private data class Reminder(
        val appointmentId: Long,
        val userId: Long,
        val clinicName: String,
        val doctorName: String,
        val dateText: String,
        val startTime: String
    )

    fun start() {
        val scheduler = newDaemonScheduledExecutor("appointment-reminder-thread")
        scheduler.scheduleHourly("AppointmentReminder", minutePast = 5) { tick() }
        logger.info("[AppointmentReminder]: 预约提醒任务已调度（每日 20:05 次日提醒 / 10:05 当日提醒）")
    }

    private suspend fun tick() {
        val hour = LocalDateTime.now().hour
        when (hour) {
            20 -> sendBatch(LocalDate.now().plusDays(1), evening = true)
            10 -> sendBatch(LocalDate.now(), evening = false)
        }
    }

    private suspend fun sendBatch(targetDate: LocalDate, evening: Boolean) {
        val reminders = suspendTransaction(DBConfig.database) {
            val rows = Appointments.selectAll()
                .where {
                    (Appointments.appointmentDate eq targetDate) and
                        (Appointments.status inList ACTIVE_STATUSES) and
                        if (evening) Appointments.remindEveningSent eq 0
                        else Appointments.remindMorningSent eq 0
                }
                .toList()
            if (rows.isEmpty()) return@suspendTransaction emptyList()

            val clinicIds = rows.map { it[Appointments.clinicId] }.distinct()
            val clinics = Clinics.selectAll()
                .where { Clinics.id inList clinicIds }
                .toList()
                .associate { it[Clinics.id] to it[Clinics.name] }

            val doctorIds = rows.map { it[Appointments.doctorId] }.distinct()
            val doctors = Doctors.selectAll()
                .where { Doctors.id inList doctorIds }
                .toList()
                .associate { it[Doctors.id] to it[Doctors.name] }

            val ids = rows.map { it[Appointments.id] }
            // 认领：先占标记位，保证下一轮 tick / 重启不会重复发
            Appointments.update({
                (Appointments.id inList ids) and
                    if (evening) Appointments.remindEveningSent eq 0
                    else Appointments.remindMorningSent eq 0
            }) {
                if (evening) it[Appointments.remindEveningSent] = 1
                else it[Appointments.remindMorningSent] = 1
            }

            rows.map { row ->
                Reminder(
                    appointmentId = row[Appointments.id],
                    userId = row[Appointments.userId],
                    clinicName = clinics[row[Appointments.clinicId]] ?: "就诊门店",
                    doctorName = doctors[row[Appointments.doctorId]] ?: "接诊医生",
                    dateText = targetDate.format(dateTextFormatter),
                    startTime = row[Appointments.startTime]
                )
            }
        }

        if (reminders.isEmpty()) return

        var ok = 0
        reminders.forEach { r ->
            try {
                if (evening) sendEvening(r) else sendMorning(r)
                ok++
            } catch (e: Exception) {
                // 单条失败不影响整批（ScheduledNotifier 内部已兜底，这里防的是兜底本身异常）
                logger.warn("[AppointmentReminder]: 预约 ${r.appointmentId} 提醒失败: ${e.message}")
            }
        }
        logger.info(
            "[AppointmentReminder]: {} 提醒已处理 {} 条（成功投递 {} 条）",
            if (evening) "次日" else "当日", reminders.size, ok
        )
    }

    /** 微信 thing 类字段限 20 字符，超长截断避免 47003 参数错误。 */
    private fun String.wxThing(): String = if (length <= 20) this else take(19) + "…"

    private suspend fun sendEvening(r: Reminder) {
        val timeText = "${r.dateText} ${r.startTime}"
        ScheduledNotifier.notify(
            userId = r.userId,
            templateKey = TPL_EVENING,
            title = "明日就诊提醒",
            content = "您已预约明天 ${r.startTime} 在${r.clinicName}由${r.doctorName}看诊，请按时到店；" +
                "如需取消或改约，请至少提前 2 小时操作。",
            subscribeData = mapOf(
                "thing1" to r.clinicName.wxThing(),
                "thing2" to r.doctorName.wxThing(),
                "time3" to timeText,
                "thing4" to "请提前15分钟到店签到"
            ),
            page = APPOINTMENT_PAGE,
            msgType = "system",
            relatedId = r.appointmentId
        )
    }

    private suspend fun sendMorning(r: Reminder) {
        val timeText = "${r.dateText} ${r.startTime}"
        ScheduledNotifier.notify(
            userId = r.userId,
            templateKey = TPL_MORNING,
            title = "今日就诊提醒",
            content = "您今天 ${r.startTime} 在${r.clinicName}由${r.doctorName}看诊，请携带有效证件准时到店签到。",
            subscribeData = mapOf(
                "thing1" to r.clinicName.wxThing(),
                "thing2" to r.doctorName.wxThing(),
                "time3" to timeText,
                "thing4" to "到店后请扫码签到"
            ),
            page = APPOINTMENT_PAGE,
            msgType = "system",
            relatedId = r.appointmentId
        )
    }
}
