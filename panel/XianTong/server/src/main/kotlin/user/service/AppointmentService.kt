package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.config.AppointmentConfig
import com.dentalclinic.common.security.CheckinCodeService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Appointment service for booking management.
 */
class AppointmentService(
    /** 门店签到码验签：只有后端签发的 HMAC 码才能解出可信 clinicId */
    private val checkinCodeService: CheckinCodeService
) : BaseService() {

    /**
     * Get all active clinics.
     */
    suspend fun getClinics(): List<ClinicDto> = dbQuery {
        Clinics.selectAll()
            .where { Clinics.status eq 1 }
            .orderBy(Clinics.sortOrder, SortOrder.ASC)
            .toList()
            .map { row ->
                ClinicDto(
                    id = row[Clinics.id],
                    name = row[Clinics.name],
                    address = row[Clinics.address],
                    phone = row[Clinics.phone],
                    openTime = row[Clinics.openTime],
                    closeTime = row[Clinics.closeTime],
                    description = row[Clinics.description],
                    coverImage = row[Clinics.coverImage]
                )
            }
    }

    /**
     * Get departments for a clinic.
     */
    suspend fun getDepartments(clinicId: Long): List<DepartmentDto> = dbQuery {
        Departments.selectAll()
            .where {
                (Departments.clinicId eq clinicId) and (Departments.status eq 1)
            }
            .toList()
            .map { row ->
                DepartmentDto(
                    id = row[Departments.id],
                    name = row[Departments.name],
                    description = row[Departments.description]
                )
            }
    }

    /**
     * Get doctors available at a clinic, optionally filtered by department.
     */
    suspend fun getDoctors(clinicId: Long, departmentId: Long?): List<DoctorDto> = dbQuery {
        // Get doctor IDs associated with this clinic
        val doctorIds = DoctorClinicRels.selectAll()
            .where { DoctorClinicRels.clinicId eq clinicId }
            .toList()
            .map { it[DoctorClinicRels.doctorId] }

        if (doctorIds.isEmpty()) {
            emptyList()
        } else {
            val rows = Doctors.selectAll()
                .where {
                    (Doctors.id inList doctorIds) and (Doctors.status eq 1)
                }
                .toList()

            rows.map { row ->
                DoctorDto(
                    id = row[Doctors.id],
                    name = row[Doctors.name],
                    title = row[Doctors.title],
                    avatar = row[Doctors.avatar],
                    introduction = row[Doctors.introduction],
                    specialties = row[Doctors.specialties]
                )
            }
        }
    }

    /**
     * Get available time slots for a doctor on a specific date.
     * Returns slots with remaining capacity.
     */
    suspend fun getAvailableSlots(doctorId: Long, date: LocalDate): List<SlotDto> = dbQuery {
        val dayOfWeek = date.dayOfWeek.value // 1=Monday, 7=Sunday

        // Get schedule for this doctor on this day of week
        val schedules = DoctorSchedules.selectAll()
            .where {
                (DoctorSchedules.doctorId eq doctorId) and
                (DoctorSchedules.dayOfWeek eq dayOfWeek) and
                (DoctorSchedules.isEnabled eq 1)
            }
            .toList()

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val slots = mutableListOf<SlotDto>()

        for (schedule in schedules) {
            val startTime = java.time.LocalTime.parse(schedule[DoctorSchedules.startTime])
            val endTime = java.time.LocalTime.parse(schedule[DoctorSchedules.endTime])
            val slotDuration = schedule[DoctorSchedules.slotDuration]
            val maxAppointments = schedule[DoctorSchedules.maxAppointments]

            // Generate time slots
            var current = startTime
            var slotIndex = 0
            while (current.isBefore(endTime)) {
                val slotEnd = current.plusMinutes(slotDuration.toLong())
                if (slotEnd.isAfter(endTime)) break

                // Check existing appointments for this slot
                val bookedCount = Appointments.selectAll()
                    .where {
                        (Appointments.doctorId eq doctorId) and
                        (Appointments.appointmentDate eq date) and
                        (Appointments.startTime eq current.format(formatter)) and
                        (Appointments.status neq "cancelled")
                    }
                    .count()

                val remaining = maxAppointments - bookedCount.toInt()
                if (remaining > 0) {
                    slots.add(SlotDto(
                        startTime = current.format(formatter),
                        endTime = slotEnd.format(formatter),
                        remaining = remaining,
                        maxAppointments = maxAppointments
                    ))
                }

                current = slotEnd
                slotIndex++
            }
        }

        slots
    }

    /**
     * Create an appointment.
     */
    suspend fun createAppointment(userId: Long, request: CreateAppointmentRequest): AppointmentResult {
        // 日期与时间都在进入事务前解析：格式错误属调用方的输入问题，应给可读提示。
        // 时间原先留到事务内才 LocalTime.parse，传错格式会抛 DateTimeParseException
        // 冒泡到路由层退化成 500，用户看不到「格式应为 HH:mm」这一有用信息。
        // 提前校验也保证了下方 `request.startTime in schedStart..<schedEnd` 的字符串比较
        // 两边都是零填充的 HH:mm，否则 "9:00" 这类值会因字典序比较而错配排班时段。
        val appointmentDate = try {
            LocalDate.parse(request.appointmentDate)
        } catch (e: DateTimeParseException) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }
        val startTimeObj = try {
            LocalTime.parse(request.startTime)
        } catch (e: DateTimeParseException) {
            return AppointmentResult.Error("时间格式错误，请使用 HH:mm")
        }

        // Pre-validation before entering transaction
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val dayOfWeek = appointmentDate.dayOfWeek.value

        // 是否允许当天预约（默认允许）
        if (!AppointmentConfig.allowSameDay() && appointmentDate.isEqual(LocalDate.now())) {
            return AppointmentResult.Error("暂不支持当天预约，请选择未来日期")
        }

        // 爽约黑名单阈值与窗口天数由系统配置下发（默认 3 次 / 30 天）
        val noShowLimit = AppointmentConfig.noShowLimit()
        val noShowWindowDays = AppointmentConfig.noShowWindowDays()

        return dbQuery {
            // 爽约黑名单：窗口内累计 no_show 达到阈值的用户禁止在线预约（需求：3 次爽约限预约 30 天）。
            // 按 appointment_date 计数实际爽约单，不依赖可能失修的 no_show_count 冗余列。
            val noShowSince = LocalDate.now().minusDays(noShowWindowDays)
            val recentNoShowCount = Appointments.selectAll()
                .where {
                    (Appointments.userId eq userId) and
                    (Appointments.status eq "no_show") and
                    (Appointments.appointmentDate greaterEq noShowSince)
                }
                .count()
            if (recentNoShowCount >= noShowLimit) {
                return@dbQuery AppointmentResult.Error(
                    "您近${noShowWindowDays}天内已有${noShowLimit}次爽约记录，暂无法在线预约，请联系门店处理"
                )
            }

            // Check if slot is still available
            val bookedCount = Appointments.selectAll()
                .where {
                    (Appointments.doctorId eq request.doctorId) and
                    (Appointments.appointmentDate eq appointmentDate) and
                    (Appointments.startTime eq request.startTime) and
                    (Appointments.status neq "cancelled")
                }
                .count()

            // Get max appointments from schedule - find the schedule that covers this time slot
            val schedule = DoctorSchedules.selectAll()
                .where {
                    (DoctorSchedules.doctorId eq request.doctorId) and
                    (DoctorSchedules.dayOfWeek eq dayOfWeek) and
                    (DoctorSchedules.isEnabled eq 1)
                }
                .toList()
                .firstOrNull { row ->
                    val schedStart = row[DoctorSchedules.startTime]
                    val schedEnd = row[DoctorSchedules.endTime]
                    request.startTime in schedStart..<schedEnd
                }

            if (schedule == null) {
                AppointmentResult.Error("该时段不可预约")
            } else {
                val maxAppointments = schedule[DoctorSchedules.maxAppointments]
                if (bookedCount >= maxAppointments) {
                    AppointmentResult.Error("该时段已满")
                } else {
                    // Check user's existing appointments to prevent duplicate booking in same slot
                    val existingAppointment = Appointments.selectAll()
                        .where {
                            (Appointments.userId eq userId) and
                            (Appointments.appointmentDate eq appointmentDate) and
                            (Appointments.startTime eq request.startTime) and
                            (Appointments.status neq "cancelled")
                        }
                        .toList()
                        .singleOrNull()

                    if (existingAppointment != null) {
                        AppointmentResult.Error("您在该时段已有预约")
                    } else {
                        // Calculate end time（startTimeObj 已在进事务前解析并校验过格式）
                        val slotDuration = schedule[DoctorSchedules.slotDuration]
                        val endTimeObj = startTimeObj.plusMinutes(slotDuration.toLong())
                        val endTime = endTimeObj.format(formatter)

                        // Create appointment
                        val appointmentId = Appointments.insert {
                            it[Appointments.userId] = userId
                            it[Appointments.clinicId] = request.clinicId
                            it[Appointments.doctorId] = request.doctorId
                            it[Appointments.departmentId] = request.departmentId
                            it[Appointments.appointmentDate] = appointmentDate
                            it[Appointments.startTime] = request.startTime
                            it[Appointments.endTime] = endTime
                            it[Appointments.status] = "pending"
                            it[Appointments.remark] = request.remark
                        } get Appointments.id

                        AppointmentResult.Success(appointmentId)
                    }
                }
            }
        }
    }

    /**
     * Get user's appointment list.
     */
    suspend fun getMyAppointments(userId: Long, status: String?): List<MyAppointmentDto> = dbQuery {
        val baseQuery = Appointments.selectAll().where { Appointments.userId eq userId }

        val rows = if (!status.isNullOrEmpty()) {
            Appointments.selectAll()
                .where { (Appointments.userId eq userId) and (Appointments.status eq status) }
                .orderBy(Appointments.appointmentDate, SortOrder.DESC)
                .orderBy(Appointments.startTime, SortOrder.DESC)
                .toList()
        } else {
            baseQuery
                .orderBy(Appointments.appointmentDate, SortOrder.DESC)
                .orderBy(Appointments.startTime, SortOrder.DESC)
                .toList()
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        rows.map { row ->
            // Fetch clinic and doctor names separately
            val clinicName = Clinics.selectAll()
                .where { Clinics.id eq row[Appointments.clinicId] }
                .toList()
                .singleOrNull()
                ?.get(Clinics.name) ?: ""

            val doctorName = Doctors.selectAll()
                .where { Doctors.id eq row[Appointments.doctorId] }
                .toList()
                .singleOrNull()
                ?.get(Doctors.name) ?: ""

            MyAppointmentDto(
                id = row[Appointments.id],
                clinicName = clinicName,
                doctorName = doctorName,
                appointmentDate = row[Appointments.appointmentDate].format(formatter),
                startTime = row[Appointments.startTime],
                endTime = row[Appointments.endTime],
                status = row[Appointments.status],
                remark = row[Appointments.remark],
                createdAt = row[Appointments.createdAt].toString()
            )
        }
    }

    /**
     * 扫码到店签到（需求 3.2.2：pending/confirmed → arrived）。
     *
     * code 为小程序上报的门店码原文（后端签发的 HMAC 签名码 `dc1.<clinicId>.<sig>`），
     * 服务端依次校验：
     * 0. 码签名合法，解出可信 clinicId（伪造/过期格式码在此直接拒绝，不碰数据库）；
     * 1. 归属本人；
     * 2. 扫码门店真实存在且处于营业状态；
     * 3. 扫码门店与预约门店一致（防用户在非预约门店、甚至家中远程扫自制码签到）；
     * 4. 状态为待就诊/已确认；
     * 5. 就诊日期就是今天。
     */
    suspend fun checkIn(userId: Long, appointmentId: Long, code: String): AppointmentResult {
        // 纯密码学校验放事务外：伪造码直接拒绝，省一次 DB 往返
        val clinicId = checkinCodeService.verify(code)
            ?: return AppointmentResult.Error("无效的门店码，请扫描门店前台二维码")

        return dbQuery {
            val appointment = Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                    (Appointments.userId eq userId)
                }
                .toList()
                .singleOrNull()
                ?: return@dbQuery AppointmentResult.Error("预约不存在")

            // 签名已证明 clinicId 由服务端签发，这里再确认门店仍真实存在且营业中
            val clinic = Clinics.selectAll()
                .where { Clinics.id eq clinicId }
                .toList()
                .singleOrNull()
                ?: return@dbQuery AppointmentResult.Error("无效的门店码，请扫描门店前台二维码")

            if (clinic[Clinics.status] != 1) {
                return@dbQuery AppointmentResult.Error("该门店已暂停营业，无法签到")
            }

            // 门店一致性：扫哪个门店的码，就必须是在该门店的预约
            if (appointment[Appointments.clinicId] != clinicId) {
                return@dbQuery AppointmentResult.Error("扫码门店与预约门店不一致，请到预约门店签到")
            }

            when (appointment[Appointments.status]) {
                "arrived" -> return@dbQuery AppointmentResult.Error("您已签到，请勿重复操作")
                "completed" -> return@dbQuery AppointmentResult.Error("预约已完成，无需签到")
                "cancelled" -> return@dbQuery AppointmentResult.Error("预约已取消，无法签到")
                "no_show" -> return@dbQuery AppointmentResult.Error("预约已爽约，无法签到")
            }

            // 仅剩 pending/confirmed 可签到；只允许就诊当天签到，防止提前几天远程签到
            if (!appointment[Appointments.appointmentDate].isEqual(LocalDate.now())) {
                return@dbQuery AppointmentResult.Error("仅就诊当天可签到")
            }

            Appointments.update({ Appointments.id eq appointmentId }) {
                it[Appointments.status] = "arrived"
                it[Appointments.arrivedAt] = LocalDateTime.now()
            }
            AppointmentResult.Success(appointmentId)
        }
    }

    /**
     * 距就诊开始是否已不足 [deadlineMinutes] 分钟（开始时间已过也视为不足，禁止自助取消/改约）。
     * 恰好等于 deadlineMinutes 仍允许（严格 <），给前台口径留一分钟余量。
     * 历史脏数据（时间格式损坏）不阻断，返回 false 交由状态机兜底。
     *
     * deadlineMinutes 由 [AppointmentConfig.cancelDeadlineMinutes] 下发（默认 120 = 2 小时），
     * 不再硬编码，管理端「系统配置」可热修改。
     */
    private fun isWithinCancelDeadline(date: LocalDate, startTime: String, deadlineMinutes: Long): Boolean =
        try {
            val start = LocalDateTime.of(date, LocalTime.parse(startTime))
            Duration.between(LocalDateTime.now(), start).toMinutes() < deadlineMinutes
        } catch (e: DateTimeParseException) {
            false
        }

    /**
     * Cancel an appointment.
     */
    suspend fun cancelAppointment(userId: Long, appointmentId: Long): AppointmentResult {
        val deadlineMinutes = AppointmentConfig.cancelDeadlineMinutes()
        return dbQuery {
        val appointment = Appointments.selectAll()
            .where {
                (Appointments.id eq appointmentId) and
                (Appointments.userId eq userId)
            }
            .toList()
            .singleOrNull()

        if (appointment == null) {
            AppointmentResult.Error("预约不存在")
        } else {
            val status = appointment[Appointments.status]
            when (status) {
                "cancelled" -> {
                    AppointmentResult.Error("预约已取消")
                }
                "completed" -> {
                    AppointmentResult.Error("预约已完成，无法取消")
                }
                else -> {
                    // 就诊前 N 分钟内不可自助取消，需联系门店（门店侧管理端不受此限）
                    if (isWithinCancelDeadline(
                            appointment[Appointments.appointmentDate],
                            appointment[Appointments.startTime],
                            deadlineMinutes
                        )
                    ) {
                        AppointmentResult.Error("距就诊不足${deadlineMinutes / 60}小时，无法自助取消，请联系门店")
                    } else {
                        // Update status
                        Appointments.update({ Appointments.id eq appointmentId }) {
                            it[Appointments.status] = "cancelled"
                            it[Appointments.cancelledAt] = java.time.LocalDateTime.now()
                            it[Appointments.cancelledBy] = userId
                        }

                        AppointmentResult.Success(appointmentId)
                    }
                }
            }
        }
        }
    }

    /**
     * Reschedule an appointment to a new date/time.
     */
    suspend fun rescheduleAppointment(userId: Long, appointmentId: Long, newDate: String, newStartTime: String): AppointmentResult {
        // Parse new date
        val appointmentDate = try {
            LocalDate.parse(newDate)
        } catch (e: DateTimeParseException) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }
        // 与 createAppointment 同理：时间格式在进事务前校验，避免退化成 500
        val startTimeObj = try {
            LocalTime.parse(newStartTime)
        } catch (e: DateTimeParseException) {
            return AppointmentResult.Error("时间格式错误，请使用 HH:mm")
        }

        // Check if new date is in the past
        if (appointmentDate.isBefore(LocalDate.now())) {
            return AppointmentResult.Error("不能改约到过去的日期")
        }

        val deadlineMinutes = AppointmentConfig.cancelDeadlineMinutes()

        // Pre-check appointment exists and status
        val preCheck = dbQuery {
            Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                    (Appointments.userId eq userId)
                }
                .toList()
                .singleOrNull()
        }

        if (preCheck == null) {
            return AppointmentResult.Error("预约不存在")
        }

        val status = preCheck[Appointments.status]
        if (status == "cancelled") {
            return AppointmentResult.Error("预约已取消")
        }
        if (status == "completed") {
            return AppointmentResult.Error("预约已完成")
        }

        // 就诊前 N 分钟内不可自助改约（与取消同规则）；已到店签到的单也不允许改约
        if (status == "arrived") {
            return AppointmentResult.Error("您已到店签到，无法改约")
        }
        if (isWithinCancelDeadline(
                preCheck[Appointments.appointmentDate],
                preCheck[Appointments.startTime],
                deadlineMinutes
            )
        ) {
            return AppointmentResult.Error("距就诊不足${deadlineMinutes / 60}小时，无法自助改约，请联系门店")
        }

        val doctorId = preCheck[Appointments.doctorId]

        return dbQuery {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")

            // Check if new slot is available (exclude current appointment)
            val bookedCount = Appointments.selectAll()
                .where {
                    (Appointments.doctorId eq doctorId) and
                    (Appointments.appointmentDate eq appointmentDate) and
                    (Appointments.startTime eq newStartTime) and
                    (Appointments.status neq "cancelled") and
                    (Appointments.id neq appointmentId)
                }
                .count()

            // Get schedule for the new time
            val dayOfWeek = appointmentDate.dayOfWeek.value
            val schedule = DoctorSchedules.selectAll()
                .where {
                    (DoctorSchedules.doctorId eq doctorId) and
                    (DoctorSchedules.dayOfWeek eq dayOfWeek) and
                    (DoctorSchedules.isEnabled eq 1)
                }
                .toList()
                .firstOrNull { row ->
                    val schedStart = row[DoctorSchedules.startTime]
                    val schedEnd = row[DoctorSchedules.endTime]
                    newStartTime in schedStart..<schedEnd
                }

            if (schedule == null) {
                AppointmentResult.Error("该时段不可预约")
            } else {
                val maxAppointments = schedule[DoctorSchedules.maxAppointments]
                if (bookedCount >= maxAppointments) {
                    AppointmentResult.Error("该时段已满")
                } else {
                    // Calculate new end time（startTimeObj 已在进事务前解析并校验过格式）
                    val slotDuration = schedule[DoctorSchedules.slotDuration]
                    val endTimeObj = startTimeObj.plusMinutes(slotDuration.toLong())
                    val newEndTime = endTimeObj.format(formatter)

                    // Update appointment
                    Appointments.update({ Appointments.id eq appointmentId }) {
                        it[Appointments.appointmentDate] = appointmentDate
                        it[Appointments.startTime] = newStartTime
                        it[Appointments.endTime] = newEndTime
                    }

                    AppointmentResult.Success(appointmentId)
                }
            }
        }
    }
}

// DTOs

@Serializable
data class ClinicDto(
    val id: Long,
    val name: String,
    val address: String,
    val phone: String,
    val openTime: String,
    val closeTime: String,
    val description: String?,
    val coverImage: String?
)

@Serializable
data class DepartmentDto(
    val id: Long,
    val name: String,
    val description: String?
)

@Serializable
data class DoctorDto(
    val id: Long,
    val name: String,
    val title: String?,
    val avatar: String?,
    val introduction: String?,
    val specialties: String?
)

@Serializable
data class SlotDto(
    val startTime: String,
    val endTime: String,
    val remaining: Int,
    val maxAppointments: Int
)

@Serializable
data class CreateAppointmentRequest(
    val clinicId: Long,
    val doctorId: Long,
    val departmentId: Long?,
    val appointmentDate: String,  // yyyy-MM-dd format
    val startTime: String,
    val remark: String?
)

sealed class AppointmentResult {
    @Serializable
    data class Success(val appointmentId: Long) : AppointmentResult()
    @Serializable
    data class Error(val message: String) : AppointmentResult()
}

@Serializable
data class MyAppointmentDto(
    val id: Long,
    val clinicName: String,
    val doctorName: String,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val remark: String?,
    val createdAt: String
)
