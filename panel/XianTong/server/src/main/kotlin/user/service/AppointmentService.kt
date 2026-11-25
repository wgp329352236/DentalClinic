package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.service.CustomerClinicRelationService
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.CouponUsageSupport
import com.dentalclinic.common.config.AppointmentConfig
import com.dentalclinic.common.security.CheckinCodeService
import com.dentalclinic.common.scheduler.ScheduledNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger("AppointmentService")

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
                    businessDays = row[Clinics.businessDays],
                    latitude = row[Clinics.latitude],
                    longitude = row[Clinics.longitude],
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

    /** 解析营业星期；空值或脏数据按全周营业处理，避免把门店误判为闭店。 */
    private fun parseBusinessDays(raw: String?): Set<Int> =
        raw.orEmpty().split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    private fun parseClock(raw: String?): LocalTime? =
        raw?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /**
     * 门店营业状态校验：返回不可预约的原因，null 表示可预约。
     */
    private suspend fun clinicClosedReason(
        clinicId: Long,
        date: LocalDate,
        startTime: LocalTime
    ): String? {
        val clinic = Clinics.selectAll()
            .where { Clinics.id eq clinicId }
            .toList()
            .singleOrNull()
            ?: return "门诊不存在"
        if (clinic[Clinics.status] != 1) return "该门店暂停营业"

        val businessDays = parseBusinessDays(clinic[Clinics.businessDays])
        if (businessDays.isNotEmpty() && date.dayOfWeek.value !in businessDays) {
            return "该门店当日不营业，请选择其他日期"
        }

        val open = parseClock(clinic[Clinics.openTime])
        val close = parseClock(clinic[Clinics.closeTime])
        if (open != null && close != null && (startTime.isBefore(open) || !startTime.isBefore(close))) {
            return "该时段不在门店营业时间内"
        }
        return null
    }

    /**
     * Get available time slots for a doctor on a specific date.
     * Returns slots with remaining capacity.
     */
    suspend fun getAvailableSlots(
        doctorId: Long,
        date: LocalDate,
        clinicId: Long? = null
    ): List<SlotDto> = dbQuery {
        val dayOfWeek = date.dayOfWeek.value // 1=Monday, 7=Sunday

        // 门店维度约束：闭店日直接无可约时段；营业时间与医生排班取交集
        val clinic = clinicId?.let { id ->
            Clinics.selectAll().where { Clinics.id eq id }.toList().singleOrNull()
        }
        if (clinic != null) {
            val businessDays = parseBusinessDays(clinic[Clinics.businessDays])
            if (businessDays.isNotEmpty() && dayOfWeek !in businessDays) return@dbQuery emptyList()
        }
        val clinicOpen = clinic?.let { parseClock(it[Clinics.openTime]) }
        val clinicClose = clinic?.let { parseClock(it[Clinics.closeTime]) }

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
            // 排班时段裁剪到门店营业时间内，避免营业时间外仍可预约
            val startTime = maxOf(
                LocalTime.parse(schedule[DoctorSchedules.startTime]),
                clinicOpen ?: LocalTime.MIN
            )
            val endTime = minOf(
                LocalTime.parse(schedule[DoctorSchedules.endTime]),
                clinicClose ?: LocalTime.MAX
            )
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
        } catch (_: DateTimeParseException) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }
        val startTimeObj = try {
            LocalTime.parse(request.startTime)
        } catch (_: DateTimeParseException) {
            return AppointmentResult.Error("时间格式错误，请使用 HH:mm")
        }

        // Pre-validation before entering transaction
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val dayOfWeek = appointmentDate.dayOfWeek.value

        // 是否允许当天预约（默认允许）
        if (!AppointmentConfig.allowSameDay() && appointmentDate.isEqual(LocalDate.now())) {
            return AppointmentResult.Error("暂不支持当天预约，请选择未来日期")
        }

        // 爽约黑名单阈值与窗口天数由系统配置下发（默认 3 次 / 30 天，阈值 0 表示关闭该限制）
        val noShowLimit = AppointmentConfig.noShowLimit()
        val noShowWindowDays = AppointmentConfig.noShowWindowDays()

        val result = try {
            dbQuery {
            // 门店闭店/非营业时间不允许下单（管理端代客预约不受此限制）
            clinicClosedReason(request.clinicId, appointmentDate, startTimeObj)?.let {
                return@dbQuery AppointmentResult.Error(it)
            }
            // 爽约黑名单：窗口内累计 no_show 达到阈值的用户禁止在线预约（需求：3 次爽约限预约 30 天）。
            // 按 appointment_date 计数实际爽约单，不依赖可能失修的 no_show_count 冗余列。
            // 阈值配置为 0 表示关闭爽约限制（否则 0 会让所有用户命中 >=0 被误拦）。
            if (noShowLimit > 0) {
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

                        // 方案 C：客户不属于单一门店，记录与本次就诊门店的业务关系
                        CustomerClinicRelationService.touch(
                            userId = userId,
                            clinicId = request.clinicId,
                            source = "appointment",
                            appointmentCount = 1
                        )

                        if (!request.couponCode.isNullOrBlank()) {
                            when (val couponResult = CouponUsageSupport.useForAppointment(
                                userId = userId,
                                couponCode = request.couponCode,
                                appointmentId = appointmentId
                            )) {
                                is CouponUsageSupport.UseResult.Success -> Unit
                                is CouponUsageSupport.UseResult.Error ->
                                    throw CouponUsageFailure(couponResult.message)
                            }
                        }

                        AppointmentResult.Success(appointmentId)
                    }
                }
                }
            }
        } catch (e: CouponUsageFailure) {
            AppointmentResult.Error(e.message ?: "优惠券使用失败")
        }
        if (result is AppointmentResult.Success) {
            logger.info(
                "[Appointment]: 用户 {} 创建预约 {} 门店={} 医生={} 时间={} {}",
                userId, result.appointmentId, request.clinicId, request.doctorId,
                request.appointmentDate, request.startTime
            )
            notifyAppointmentSubmitted(userId, result.appointmentId, request)
        } else if (result is AppointmentResult.Error) {
            logger.info("[Appointment]: 用户 {} 创建预约失败: {}", userId, result.message)
        }
        return result
    }

    /**
     * 预约提交成功后异步通知用户等待门店确认。
     *
     * 通知必须放在创建事务提交之后：查询门店名失败或微信订阅消息异常都不能回滚预约。
     */
    private suspend fun notifyAppointmentSubmitted(
        userId: Long,
        appointmentId: Long,
        request: CreateAppointmentRequest
    ) {
        try {
            val clinicName = dbQuery {
                Clinics.selectAll()
                    .where { Clinics.id eq request.clinicId }
                    .toList()
                    .singleOrNull()
                    ?.get(Clinics.name)
                    ?: "就诊门店"
            }
            val appointmentTime = "${request.appointmentDate} ${request.startTime}"
            ScheduledNotifier.notifyAsync(
                userId = userId,
                templateKey = "appointment_submitted",
                title = "预约已提交",
                content = "您的预约已提交，等待门店确认。就诊时间：$appointmentTime，门店：$clinicName。",
                subscribeData = mapOf(
                    "date1" to request.appointmentDate,
                    "time2" to request.startTime,
                    "thing3" to clinicName.let { if (it.length <= 20) it else it.take(19) + "…" }
                ),
                page = "/pages/appointments/appointments",
                msgType = "system",
                relatedId = appointmentId
            )
        } catch (e: Exception) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            logger.warn("[Appointment]: 预约 {} 提交通知失败，不影响预约结果: {}", appointmentId, e.message)
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
        val clinicIds = rows.map { it[Appointments.clinicId] }.distinct()
        val doctorIds = rows.map { it[Appointments.doctorId] }.distinct()
        val departmentIds = rows.mapNotNull { it[Appointments.departmentId] }.distinct()
        val clinicsMap = if (clinicIds.isEmpty()) emptyMap() else {
            Clinics.selectAll().where { Clinics.id inList clinicIds }.toList()
                .associateBy { it[Clinics.id] }
        }
        val doctorsMap = if (doctorIds.isEmpty()) emptyMap() else {
            Doctors.selectAll().where { Doctors.id inList doctorIds }.toList()
                .associateBy { it[Doctors.id] }
        }
        val departmentsMap = if (departmentIds.isEmpty()) emptyMap() else {
            Departments.selectAll().where { Departments.id inList departmentIds }.toList()
                .associateBy { it[Departments.id] }
        }
        val appointmentIds = rows.map { it[Appointments.id] }.distinct()
        val couponsByAppointment = if (appointmentIds.isEmpty()) emptyMap() else {
            UserCoupons.selectAll()
                .where {
                    (UserCoupons.userId eq userId) and
                        (UserCoupons.appointmentId inList appointmentIds) and
                        (UserCoupons.status eq "used")
                }
                .toList()
                .groupBy { it[UserCoupons.appointmentId] }
        }
        val couponTemplateIds = couponsByAppointment.values
            .flatten()
            .map { it[UserCoupons.templateId] }
            .distinct()
        val couponTemplatesMap = if (couponTemplateIds.isEmpty()) emptyMap() else {
            CouponTemplates.selectAll()
                .where { CouponTemplates.id inList couponTemplateIds }
                .toList()
                .associateBy { it[CouponTemplates.id] }
        }

        rows.map { row ->
            val departmentId = row[Appointments.departmentId]
            val department = departmentId?.let { departmentsMap[it] }
            val coupon = couponsByAppointment[row[Appointments.id]]?.firstOrNull()
            val couponTemplate = coupon?.let { couponTemplatesMap[it[UserCoupons.templateId]] }

            MyAppointmentDto(
                id = row[Appointments.id],
                clinicId = row[Appointments.clinicId],
                clinicName = clinicsMap[row[Appointments.clinicId]]?.get(Clinics.name) ?: "",
                doctorId = row[Appointments.doctorId],
                doctorName = doctorsMap[row[Appointments.doctorId]]?.get(Doctors.name) ?: "",
                departmentId = departmentId,
                departmentName = department?.get(Departments.name),
                appointmentDate = row[Appointments.appointmentDate].format(formatter),
                startTime = row[Appointments.startTime],
                endTime = row[Appointments.endTime],
                status = row[Appointments.status],
                remark = row[Appointments.remark],
                cancelRequestStatus = row[Appointments.cancelRequestStatus],
                cancelRequestReason = row[Appointments.cancelRequestReason],
                cancelRequestedAt = row[Appointments.cancelRequestedAt]?.toString(),
                createdAt = row[Appointments.createdAt].toString(),
                couponCode = coupon?.get(UserCoupons.couponCode),
                couponName = couponTemplate?.get(CouponTemplates.name),
                couponFaceValue = couponTemplate?.get(CouponTemplates.faceValue)?.toDouble()
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
            ?: run {
                logger.warn("[Appointment]: 用户 {} 签到失败：门店码无效 appointmentId={}", userId, appointmentId)
                return AppointmentResult.Error("无效的门店码，请扫描门店前台二维码")
            }

        return dbQuery {
            val appointment = Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                    (Appointments.userId eq userId)
                }
                .forUpdate()
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
                "pending", "confirmed" -> Unit
                else -> return@dbQuery AppointmentResult.Error("当前预约状态无法签到")
            }

            if (appointment[Appointments.cancelRequestStatus] == "pending") {
                return@dbQuery AppointmentResult.Error("取消申请审核中，暂不能签到，请等待门店处理")
            }

            // 仅剩 pending/confirmed 可签到；只允许就诊当天签到，防止提前几天远程签到
            if (!appointment[Appointments.appointmentDate].isEqual(LocalDate.now())) {
                return@dbQuery AppointmentResult.Error("仅就诊当天可签到")
            }

            Appointments.update({ Appointments.id eq appointmentId }) {
                it[Appointments.status] = "arrived"
                it[Appointments.arrivedAt] = LocalDateTime.now()
            }
            logger.info("[Appointment]: 用户 {} 预约 {} 签到成功 门店={}", userId, appointmentId, clinicId)
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
        } catch (_: DateTimeParseException) {
            false
        }

    /**
     * Cancel an appointment.
     */
    suspend fun cancelAppointment(userId: Long, appointmentId: Long): AppointmentCancelResult {
        val deadlineMinutes = AppointmentConfig.cancelDeadlineMinutes()
        return dbQuery {
            val appointment = Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                        (Appointments.userId eq userId)
                }
                .forUpdate()
                .toList()
                .singleOrNull()
                ?: return@dbQuery AppointmentCancelResult.Error("预约不存在")

            when (appointment[Appointments.status]) {
                "cancelled" -> return@dbQuery AppointmentCancelResult.Error("预约已取消")
                "completed" -> return@dbQuery AppointmentCancelResult.Error("预约已完成，无法取消")
                "arrived" -> return@dbQuery AppointmentCancelResult.Error("您已到店签到，无法取消")
                "no_show" -> return@dbQuery AppointmentCancelResult.Error("预约已爽约，无法取消")
                "pending", "confirmed" -> Unit
                else -> return@dbQuery AppointmentCancelResult.Error("当前预约状态无法取消")
            }

            when (appointment[Appointments.cancelRequestStatus]) {
                "pending" -> return@dbQuery AppointmentCancelResult.Error("取消申请已提交，请等待门店处理")
                "approved" -> return@dbQuery AppointmentCancelResult.Error("取消申请已通过，请勿重复提交")
            }

            val now = LocalDateTime.now()
            if (isWithinCancelDeadline(
                    appointment[Appointments.appointmentDate],
                    appointment[Appointments.startTime],
                    deadlineMinutes
                )
            ) {
                Appointments.update({ Appointments.id eq appointmentId }) {
                    it[cancelRequestStatus] = "pending"
                    it[cancelRequestReason] = "用户申请取消，等待门店确认"
                    it[cancelRequestedAt] = now
                    it[cancelReviewedBy] = null
                    it[cancelReviewedAt] = null
                    it[updatedAt] = now
                }
                logger.info(
                    "[Appointment]: 用户 {} 预约 {} 距就诊不足 {} 分钟，提交取消申请",
                    userId, appointmentId, deadlineMinutes
                )
                AppointmentCancelResult.Requested(appointmentId)
            } else {
                Appointments.update({ Appointments.id eq appointmentId }) {
                    it[status] = "cancelled"
                    it[cancelReason] = "用户取消"
                    it[cancelledBy] = userId
                    it[cancelledAt] = now
                    it[cancelRequestStatus] = null
                    it[cancelRequestReason] = null
                    it[cancelRequestedAt] = null
                    it[cancelReviewedBy] = null
                    it[cancelReviewedAt] = null
                    it[updatedAt] = now
                }
                CouponUsageSupport.releaseForAppointment(userId, appointmentId)
                logger.info("[Appointment]: 用户 {} 预约 {} 取消成功", userId, appointmentId)
                AppointmentCancelResult.Cancelled(appointmentId)
            }
        }
    }

    /**
     * Reschedule an appointment to a new date/time.
     */
    suspend fun rescheduleAppointment(
        userId: Long,
        appointmentId: Long,
        newDate: String,
        newStartTime: String,
        couponCode: String? = null
    ): AppointmentResult {
        // Parse new date
        val appointmentDate = try {
            LocalDate.parse(newDate)
        } catch (_: DateTimeParseException) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }
        // 与 createAppointment 同理：时间格式在进事务前校验，避免退化成 500
        val startTimeObj = try {
            LocalTime.parse(newStartTime)
        } catch (_: DateTimeParseException) {
            return AppointmentResult.Error("时间格式错误，请使用 HH:mm")
        }

        // Check if new date is in the past
        if (appointmentDate.isBefore(LocalDate.now())) {
            return AppointmentResult.Error("不能改约到过去的日期")
        }

        val deadlineMinutes = AppointmentConfig.cancelDeadlineMinutes()

        return try {
            dbQuery {
            // 改约读取必须与审批/取消共用行锁：否则读取后门店刚好同意取消，
            // 后续更新可能把已取消预约重新写回 pending。
            val appointment = Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                        (Appointments.userId eq userId)
                }
                .forUpdate()
                .toList()
                .singleOrNull()
                ?: return@dbQuery AppointmentResult.Error("预约不存在")

            when (appointment[Appointments.status]) {
                "cancelled" -> return@dbQuery AppointmentResult.Error("预约已取消")
                "completed" -> return@dbQuery AppointmentResult.Error("预约已完成")
                "arrived" -> return@dbQuery AppointmentResult.Error("您已到店签到，无法改约")
                "no_show" -> return@dbQuery AppointmentResult.Error("预约已爽约，无法改约")
                "pending", "confirmed" -> Unit
                else -> return@dbQuery AppointmentResult.Error("当前预约状态无法改约")
            }
            if (appointment[Appointments.cancelRequestStatus] == "pending") {
                return@dbQuery AppointmentResult.Error("取消申请审核中，暂不能改约")
            }
            if (isWithinCancelDeadline(
                    appointment[Appointments.appointmentDate],
                    appointment[Appointments.startTime],
                    deadlineMinutes
                )
            ) {
                return@dbQuery AppointmentResult.Error("距就诊不足${deadlineMinutes / 60}小时，无法自助改约，请联系门店")
            }
            if (appointment[Appointments.appointmentDate] == appointmentDate &&
                appointment[Appointments.startTime] == newStartTime
            ) {
                return@dbQuery AppointmentResult.Error("新预约时间与原预约相同")
            }

            // 改约同样受门店营业日/营业时间约束
            clinicClosedReason(appointment[Appointments.clinicId], appointmentDate, startTimeObj)?.let {
                return@dbQuery AppointmentResult.Error(it)
            }

            val doctorId = appointment[Appointments.doctorId]
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

            // 用户本人不能在同一时段同时保留两张有效预约（可改到其他医生）
            val userBookedCount = Appointments.selectAll()
                .where {
                    (Appointments.userId eq userId) and
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
                } else if (userBookedCount > 0) {
                    AppointmentResult.Error("您在该时段已有预约")
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
                        it[Appointments.status] = "pending"
                        it[Appointments.remindEveningSent] = 0
                        it[Appointments.remindMorningSent] = 0
                        it[Appointments.cancelReason] = null
                        it[Appointments.cancelledBy] = null
                        it[Appointments.cancelledAt] = null
                        it[Appointments.cancelRequestStatus] = null
                        it[Appointments.cancelRequestReason] = null
                        it[Appointments.cancelRequestedAt] = null
                        it[Appointments.cancelReviewedBy] = null
                        it[Appointments.cancelReviewedAt] = null
                        it[Appointments.updatedAt] = LocalDateTime.now()
                    }

                    if (couponCode != null) {
                        CouponUsageSupport.releaseForAppointment(userId, appointmentId)
                        if (couponCode.isNotBlank()) {
                            when (val couponResult = CouponUsageSupport.useForAppointment(
                                userId = userId,
                                couponCode = couponCode,
                                appointmentId = appointmentId
                            )) {
                                is CouponUsageSupport.UseResult.Success -> Unit
                                is CouponUsageSupport.UseResult.Error ->
                                    throw CouponUsageFailure(couponResult.message)
                            }
                        }
                    }

                    AppointmentResult.Success(appointmentId)
                }
                }
            }
        } catch (e: CouponUsageFailure) {
            AppointmentResult.Error(e.message ?: "优惠券使用失败")
        }
    }

    private class CouponUsageFailure(message: String) : RuntimeException(message)
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
    val businessDays: String,
    val latitude: Double?,
    val longitude: Double?,
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
    val remark: String?,
    val couponCode: String? = null
)

sealed class AppointmentResult {
    @Serializable
    data class Success(val appointmentId: Long) : AppointmentResult()
    @Serializable
    data class Error(val message: String) : AppointmentResult()
}

sealed class AppointmentCancelResult {
    @Serializable
    data class Cancelled(val appointmentId: Long) : AppointmentCancelResult()
    @Serializable
    data class Requested(val appointmentId: Long) : AppointmentCancelResult()
    @Serializable
    data class Error(val message: String) : AppointmentCancelResult()
}

@Serializable
data class MyAppointmentDto(
    val id: Long,
    val clinicId: Long,
    val clinicName: String,
    val doctorId: Long,
    val doctorName: String,
    val departmentId: Long?,
    val departmentName: String?,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val remark: String?,
    val cancelRequestStatus: String?,
    val cancelRequestReason: String?,
    val cancelRequestedAt: String?,
    val createdAt: String,
    val couponCode: String? = null,
    val couponName: String? = null,
    val couponFaceValue: Double? = null
)
