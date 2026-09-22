package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.ClinicScope
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.ForbiddenException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.scheduler.ScheduledNotifier
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 门诊管理
 */
class ClinicService : BaseService() {

    suspend fun listClinics(scope: ClinicScope = ClinicScope.ALL): List<ClinicResponse> = dbQuery {
        // 未授权任何门店的非超管账号直接返回空，避免对空列表执行 IN ()
        if (scope.isEmpty) return@dbQuery emptyList()
        var query = Clinics.selectAll()
        if (!scope.all) {
            query = query.andWhere { Clinics.id inList scope.clinicIds }
        }
        query
            .orderBy(Clinics.sortOrder, SortOrder.ASC)
            .toList()
            .map { r ->
                ClinicResponse(
                    id = r[Clinics.id],
                    name = r[Clinics.name],
                    address = r[Clinics.address],
                    phone = r[Clinics.phone],
                    openTime = r[Clinics.openTime],
                    closeTime = r[Clinics.closeTime],
                    description = r[Clinics.description],
                    coverImage = r[Clinics.coverImage],
                    status = r[Clinics.status],
                    sortOrder = r[Clinics.sortOrder]
                )
            }
    }

    /** 按主键查单个门店，不存在返回 null（签到码签发前校验门店真实性）。 */
    suspend fun getClinic(id: Long): ClinicResponse? = dbQuery {
        Clinics.selectAll()
            .where { Clinics.id eq id }
            .toList()
            .singleOrNull()
            ?.let { r ->
                ClinicResponse(
                    id = r[Clinics.id],
                    name = r[Clinics.name],
                    address = r[Clinics.address],
                    phone = r[Clinics.phone],
                    openTime = r[Clinics.openTime],
                    closeTime = r[Clinics.closeTime],
                    description = r[Clinics.description],
                    coverImage = r[Clinics.coverImage],
                    status = r[Clinics.status],
                    sortOrder = r[Clinics.sortOrder]
                )
            }
    }

    suspend fun createClinic(req: ClinicRequest): Long = dbQuery {
        Clinics.insert {
            it[name] = req.name
            it[address] = req.address
            it[phone] = req.phone
            it[openTime] = req.openTime
            it[closeTime] = req.closeTime
            it[description] = req.description
            it[coverImage] = req.coverImage
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[status] = req.status
            it[sortOrder] = req.sortOrder
        } get Clinics.id
    }

    suspend fun updateClinic(id: Long, req: ClinicRequest): Unit = dbQuery {
        Clinics.update({ Clinics.id eq id }) {
            it[name] = req.name
            it[address] = req.address
            it[phone] = req.phone
            it[openTime] = req.openTime
            it[closeTime] = req.closeTime
            it[description] = req.description
            it[coverImage] = req.coverImage
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[status] = req.status
            it[sortOrder] = req.sortOrder
        }
    }

    suspend fun deleteClinic(id: Long): Unit = dbQuery {
        Clinics.deleteWhere { Clinics.id eq id }
    }
}

/**
 * 科室管理
 */
class DepartmentService : BaseService() {

    suspend fun listDepartments(clinicId: Long?): List<DepartmentResponse> = dbQuery {
        val query = if (clinicId != null) {
            Departments.selectAll()
                .where { (Departments.status eq 1) and (Departments.clinicId eq clinicId) }
        } else {
            Departments.selectAll()
                .where { Departments.status eq 1 }
        }
        query.toList().map { r ->
            DepartmentResponse(
                id = r[Departments.id],
                clinicId = r[Departments.clinicId],
                deptName = r[Departments.name],
                description = r[Departments.description]
            )
        }
    }

    suspend fun createDepartment(req: DepartmentRequest): Long = dbQuery {
        Departments.insert {
            it[clinicId] = req.clinicId
            it[name] = req.name
            it[description] = req.description
            it[status] = 1
        } get Departments.id
    }

    suspend fun updateDepartment(id: Long, req: DepartmentRequest): Unit = dbQuery {
        Departments.update({ Departments.id eq id }) {
            it[clinicId] = req.clinicId
            it[name] = req.name
            it[description] = req.description
        }
    }

    suspend fun deleteDepartment(id: Long): Unit = dbQuery {
        Departments.deleteWhere { Departments.id eq id }
    }
}

/**
 * 医生管理
 */
class DoctorService : BaseService() {

    suspend fun listDoctors(): List<DoctorResponse> = dbQuery {
        val doctors = Doctors.selectAll()
            .where { Doctors.status eq 1 }
            .toList()

        // Batch load clinic relations to avoid N+1
        val doctorIds = doctors.map { it[Doctors.id] }
        val allRels = if (doctorIds.isNotEmpty()) {
            DoctorClinicRels.selectAll()
                .where { DoctorClinicRels.doctorId inList doctorIds }
                .toList()
        } else emptyList()
        val relsByDoctor = allRels.groupBy { it[DoctorClinicRels.doctorId] }

        doctors.map { row ->
            val cids = relsByDoctor[row[Doctors.id]]
                ?.map { it[DoctorClinicRels.clinicId] } ?: emptyList()
            DoctorResponse(
                id = row[Doctors.id],
                name = row[Doctors.name],
                title = row[Doctors.title],
                specialties = row[Doctors.specialties],
                introduction = row[Doctors.introduction],
                avatar = row[Doctors.avatar],
                clinicIds = cids
            )
        }
    }

    suspend fun createDoctor(req: DoctorRequest): Long = dbQuery {
        val id = Doctors.insert {
            it[name] = req.name
            it[title] = req.title
            it[specialties] = req.specialties
            it[introduction] = req.introduction
            it[avatar] = req.avatar
            it[status] = 1
        } get Doctors.id
        for (c in req.clinicIds) {
            DoctorClinicRels.insert {
                it[doctorId] = id
                it[clinicId] = c
            }
        }
        id
    }

    suspend fun updateDoctor(id: Long, req: DoctorRequest): Unit = dbQuery {
        Doctors.update({ Doctors.id eq id }) {
            it[name] = req.name
            it[title] = req.title
            it[specialties] = req.specialties
            it[introduction] = req.introduction
            it[avatar] = req.avatar
            it[status] = req.status
        }
        DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
        for (c in req.clinicIds) {
            DoctorClinicRels.insert {
                it[doctorId] = id
                it[clinicId] = c
            }
        }
    }

    suspend fun deleteDoctor(id: Long): Unit = dbQuery {
        DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
        Doctors.deleteWhere { Doctors.id eq id }
    }
}

/**
 * 排班管理
 */
class ScheduleService : BaseService() {

    suspend fun listSchedules(doctorId: Long): List<ScheduleResponse> = dbQuery {
        DoctorSchedules.selectAll()
            .where { DoctorSchedules.doctorId eq doctorId }
            .orderBy(DoctorSchedules.dayOfWeek, SortOrder.ASC)
            .orderBy(DoctorSchedules.startTime, SortOrder.ASC)
            .toList()
            .map { r ->
                ScheduleResponse(
                    id = r[DoctorSchedules.id],
                    doctorId = r[DoctorSchedules.doctorId],
                    dayOfWeek = r[DoctorSchedules.dayOfWeek],
                    startTime = r[DoctorSchedules.startTime],
                    endTime = r[DoctorSchedules.endTime],
                    maxAppointments = r[DoctorSchedules.maxAppointments],
                    isEnabled = r[DoctorSchedules.isEnabled]
                )
            }
    }

    suspend fun createSchedule(req: ScheduleRequest): Long = dbQuery {
        DoctorSchedules.insert {
            it[doctorId] = req.doctorId
            it[dayOfWeek] = req.dayOfWeek
            it[startTime] = req.startTime
            it[endTime] = req.endTime
            it[maxAppointments] = req.maxAppointments
            it[slotDuration] = req.slotDuration
            it[isEnabled] = 1
        } get DoctorSchedules.id
    }

    suspend fun deleteSchedule(id: Long): Unit = dbQuery {
        DoctorSchedules.deleteWhere { DoctorSchedules.id eq id }
    }
}

/**
 * 预约管理（Admin 端）
 */
class AppointmentAdminService : BaseService() {

    /**
     * 预约列表。
     *
     * page/pageSize 均为可选：不传时返回全量（下拉选择器等场景需一次拿全），
     * 传了才分页。分页时 total 是符合筛选条件的总行数，而非当页行数。
     */
    suspend fun listAppointments(
        status: String?, clinicId: Long?, doctorId: Long?, userId: Long? = null,
        dateFrom: String? = null, dateTo: String? = null,
        page: Int? = null, pageSize: Int? = null,
        scope: ClinicScope = ClinicScope.ALL
    ): AppointmentListResponse = dbQuery {
        // 门店数据隔离：无全店权限且未授权任何门店时直接空结果
        if (scope.isEmpty) {
            return@dbQuery AppointmentListResponse(list = emptyList(), total = 0, page = page, pageSize = pageSize)
        }
        var query = Appointments.selectAll()
        if (!status.isNullOrBlank()) {
            query = query.andWhere { Appointments.status eq status }
        }
        if (clinicId != null) {
            query = query.andWhere { Appointments.clinicId eq clinicId }
        }
        if (!scope.all) {
            query = query.andWhere { Appointments.clinicId inList scope.clinicIds }
        }
        if (doctorId != null) {
            query = query.andWhere { Appointments.doctorId eq doctorId }
        }
        // 按客户过滤：供「录入消费」弹窗联动该客户的预约
        if (userId != null) {
            query = query.andWhere { Appointments.userId eq userId }
        }
        // 预约日期范围（按 appointment_date 而非创建时间，看板/翻页都按就诊日）
        if (dateFrom != null) {
            query = query.andWhere { Appointments.appointmentDate greaterEq parseApptDate(dateFrom) }
        }
        if (dateTo != null) {
            query = query.andWhere { Appointments.appointmentDate lessEq parseApptDate(dateTo) }
        }

        val total = query.count()
        var rowsQuery = query.orderBy(Appointments.createdAt, SortOrder.DESC)
        if (page != null && pageSize != null) {
            rowsQuery = rowsQuery.limit(pageSize).offset(((page - 1) * pageSize).toLong())
        }
        val allRows = rowsQuery.toList()

        // Batch load reference data to avoid N+1 queries
        val userIds = allRows.map { it[Appointments.userId] }.distinct()
        val clinicIds = allRows.map { it[Appointments.clinicId] }.distinct()
        val doctorIds = allRows.map { it[Appointments.doctorId] }.distinct()
        val deptIds = allRows.mapNotNull { it[Appointments.departmentId] }.distinct()

        val usersMap = if (userIds.isNotEmpty()) {
            Users.selectAll().where { Users.id inList userIds }.toList()
                .associateBy { it[Users.id] }
        } else emptyMap()

        val clinicsMap = if (clinicIds.isNotEmpty()) {
            Clinics.selectAll().where { Clinics.id inList clinicIds }.toList()
                .associateBy { it[Clinics.id] }
        } else emptyMap()

        val doctorsMap = if (doctorIds.isNotEmpty()) {
            Doctors.selectAll().where { Doctors.id inList doctorIds }.toList()
                .associateBy { it[Doctors.id] }
        } else emptyMap()

        val deptsMap = if (deptIds.isNotEmpty()) {
            Departments.selectAll().where { Departments.id inList deptIds }.toList()
                .associateBy { it[Departments.id] }
        } else emptyMap()

        val list = allRows.map { row ->
            val user = usersMap[row[Appointments.userId]]
            val clinic = clinicsMap[row[Appointments.clinicId]]
            val doctor = doctorsMap[row[Appointments.doctorId]]
            val deptId = row[Appointments.departmentId]
            val dept = deptId?.let { deptsMap[it] }

            AppointmentAdminResponse(
                id = row[Appointments.id],
                userId = row[Appointments.userId],
                userName = user?.get(Users.realName) ?: user?.get(Users.nickname),
                userPhone = user?.get(Users.phone),
                clinicId = row[Appointments.clinicId],
                clinicName = clinic?.get(Clinics.name) ?: "",
                doctorId = row[Appointments.doctorId],
                doctorName = doctor?.get(Doctors.name) ?: "",
                departmentId = deptId,
                departmentName = dept?.get(Departments.name),
                appointmentDate = row[Appointments.appointmentDate].toString(),
                startTime = row[Appointments.startTime],
                endTime = row[Appointments.endTime],
                status = row[Appointments.status],
                remark = row[Appointments.remark],
                createdAt = row[Appointments.createdAt].toString()
            )
        }

        // 用可序列化 DTO 包装：原先返回 Map<String, Any>，Any 无序列化器，
        // 会在响应阶段抛 SerializationException 使整个接口 400。
        AppointmentListResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    suspend fun getAppointmentDetail(id: Long): AppointmentDetailResponse = dbQuery {
        val appointment = Appointments.selectAll()
            .where { Appointments.id eq id }
            .toList().firstOrNull()
            ?: throw ValidationException("预约不存在")

        val user = Users.selectAll()
            .where { Users.id eq appointment[Appointments.userId] }
            .toList().firstOrNull()

        val clinic = Clinics.selectAll()
            .where { Clinics.id eq appointment[Appointments.clinicId] }
            .toList().firstOrNull()

        val doctor = Doctors.selectAll()
            .where { Doctors.id eq appointment[Appointments.doctorId] }
            .toList().firstOrNull()

        val deptId = appointment[Appointments.departmentId]
        val dept = if (deptId != null) {
            Departments.selectAll()
                .where { Departments.id eq deptId }
                .toList().firstOrNull()
        } else null

        val consumptionRows = ConsumptionRecords.selectAll()
            .where { ConsumptionRecords.appointmentId eq id }
            .toList()

        AppointmentDetailResponse(
            appointment = AppointmentDetailDto(
                id = appointment[Appointments.id],
                userId = appointment[Appointments.userId],
                userName = user?.get(Users.realName) ?: user?.get(Users.nickname),
                userPhone = user?.get(Users.phone),
                clinicId = appointment[Appointments.clinicId],
                clinicName = clinic?.get(Clinics.name),
                doctorId = appointment[Appointments.doctorId],
                doctorName = doctor?.get(Doctors.name),
                departmentId = deptId,
                departmentName = dept?.get(Departments.name),
                appointmentDate = appointment[Appointments.appointmentDate].toString(),
                startTime = appointment[Appointments.startTime],
                endTime = appointment[Appointments.endTime],
                status = appointment[Appointments.status],
                diagnosis = appointment[Appointments.diagnosis],
                treatmentPlan = appointment[Appointments.treatmentPlan],
                remark = appointment[Appointments.remark],
                arrivedAt = appointment[Appointments.arrivedAt]?.toString(),
                completedAt = appointment[Appointments.completedAt]?.toString(),
                createdAt = appointment[Appointments.createdAt].toString()
            ),
            consumption = ConsumptionSummaryDto(
                totalAmount = consumptionRows.sumOf { it[ConsumptionRecords.amount].toDouble() },
                recordCount = consumptionRows.size
            )
        )
    }

    suspend fun updateStatus(id: Long, req: AppointmentStatusRequest): Unit = dbQuery {
        Appointments.update({ Appointments.id eq id }) {
            it[status] = req.status
            if (req.remark != null) {
                it[remark] = req.remark
            }
            when (req.status) {
                "arrived" -> it[arrivedAt] = LocalDateTime.now()
                "completed" -> it[completedAt] = LocalDateTime.now()
                "cancelled" -> it[cancelledAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun cancelAppointment(id: Long, reason: String?): Unit = dbQuery {
        val appointment = Appointments.selectAll()
            .where { Appointments.id eq id }
            .toList()
            .firstOrNull()
        
        Appointments.update({ Appointments.id eq id }) {
            it[status] = "cancelled"
            it[cancelReason] = reason
            it[cancelledAt] = LocalDateTime.now()
        }
        
        // P1-6: 预约取消通知（微信订阅消息优先，降级站内信）
        if (appointment != null) {
            val userId = appointment[Appointments.userId]
            val reasonText = if (reason.isNullOrBlank()) "未说明" else reason
            ScheduledNotifier.notify(
                userId = userId,
                templateKey = "appointment_cancelled",
                title = "预约已取消",
                content = "您的预约已取消，原因：$reasonText。如需重新预约，请在小程序操作。",
                subscribeData = mapOf(
                    "date1" to appointment[Appointments.appointmentDate].toString(),
                    "time2" to "${appointment[Appointments.startTime]}-${appointment[Appointments.endTime]}",
                    "thing3" to reasonText.take(20)
                ),
                page = "/pages/appointments/appointments",
                msgType = "system",
                relatedId = id
            )
        }
    }

    /**
     * 确认预约（仅 pending 状态可确认，流转到 confirmed）。
     *
     * 与 updateStatus 的区别：显式校验当前状态，避免误把已取消/已完成的单改回 confirmed，
     * 给前台一个语义清晰的「确认」动作而非通用状态变更。
     */
    suspend fun confirmAppointment(id: Long): Unit = dbQuery {
        val appointment = Appointments.selectAll()
            .where { Appointments.id eq id }
            .toList()
            .firstOrNull()
            ?: throw ValidationException("预约不存在")
        val currentStatus = appointment[Appointments.status]
        if (currentStatus != "pending") {
            throw ValidationException("当前状态「${statusText(currentStatus)}」不可确认，仅待确认的预约可确认")
        }
        Appointments.update({ Appointments.id eq id }) {
            it[status] = "confirmed"
        }
        
        // P1-6: 预约确认通知（微信订阅消息优先，降级站内信）
        val userId = appointment[Appointments.userId]
        val appointmentDate = appointment[Appointments.appointmentDate]
        val startTime = appointment[Appointments.startTime]
        ScheduledNotifier.notify(
            userId = userId,
            templateKey = "appointment_confirmed",
            title = "预约已确认",
            content = "您的预约已确认，就诊时间：$appointmentDate $startTime，请准时到院。",
            subscribeData = mapOf(
                "date1" to appointmentDate.toString(),
                "time2" to startTime,
                "thing3" to "预约已确认"
            ),
            page = "/pages/appointments/appointments",
            msgType = "system",
            relatedId = id
        )
    }

    /**
     * 拒绝预约（仅 pending 状态可拒绝，流转到 cancelled，必须填写拒绝原因）。
     */
    suspend fun rejectAppointment(id: Long, reason: String): Unit = dbQuery {
        if (reason.isBlank()) {
            throw ValidationException("请填写拒绝原因")
        }
        val appointment = Appointments.selectAll()
            .where { Appointments.id eq id }
            .toList()
            .firstOrNull()
            ?: throw ValidationException("预约不存在")
        val currentStatus = appointment[Appointments.status]
        if (currentStatus != "pending") {
            throw ValidationException("当前状态「${statusText(currentStatus)}」不可拒绝，仅待确认的预约可拒绝")
        }
        Appointments.update({ Appointments.id eq id }) {
            it[status] = "cancelled"
            it[cancelReason] = reason
            it[cancelledAt] = LocalDateTime.now()
        }

        // P1-6: 预约拒绝通知（微信订阅消息优先，降级站内信）
        val userId = appointment[Appointments.userId]
        ScheduledNotifier.notify(
            userId = userId,
            templateKey = "appointment_rejected",
            title = "预约申请未通过",
            content = "您的预约申请未通过，原因：${reason.take(50)}。如有疑问请联系门店。",
            subscribeData = mapOf(
                "date1" to appointment[Appointments.appointmentDate].toString(),
                "time2" to appointment[Appointments.startTime],
                "thing3" to reason.take(20)
            ),
            page = "/pages/appointments/appointments",
            msgType = "system",
            relatedId = id
        )
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> "待确认"
        "confirmed" -> "已确认"
        "arrived" -> "已到店"
        "completed" -> "已完成"
        "cancelled" -> "已取消"
        "no_show" -> "爽约"
        else -> status
    }

    suspend fun updateMedicalRecord(id: Long, req: MedicalRecordUpdateRequest): Unit = dbQuery {
        Appointments.update({ Appointments.id eq id }) {
            if (req.diagnosis != null) {
                it[diagnosis] = req.diagnosis
            }
            if (req.treatmentPlan != null) {
                it[treatmentPlan] = req.treatmentPlan
            }
        }
    }

    /**
     * 代客预约：前台/客服替客户下单。
     * 与 C 端 createAppointment 的差异：
     * ① 受门店数据权限约束（scope 外门店禁止下单）；
     * ② 跳过爽约黑名单（门店人工受理，豁免 30 天 3 次爽约的在线限制）；
     * ③ 初始状态直接为 confirmed（前台已与客户确认，无需再次确认）。
     */
    suspend fun createAppointment(req: AdminCreateAppointmentRequest, scope: ClinicScope): Long {
        // 进事务前解析日期时间：格式错误属调用方输入问题，给可读 400 而非冒泡成 500。
        // 同时保证下方时段匹配的字符串比较两边都是零填充 HH:mm，避免 "9:00" 字典序错配。
        val appointmentDate = parseApptDate(req.appointmentDate)
        val startTimeObj = try {
            LocalTime.parse(req.startTime)
        } catch (e: DateTimeParseException) {
            throw ValidationException("时间格式应为 HH:mm，收到：${req.startTime}")
        }

        if (!scope.all && req.clinicId !in scope.clinicIds) {
            throw ForbiddenException("无权在该门店代客预约")
        }

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val dayOfWeek = appointmentDate.dayOfWeek.value

        return dbQuery {
            // 客户存在性
            val userCount = Users.selectAll().where { Users.id eq req.userId }.count()
            if (userCount == 0L) {
                throw ValidationException("客户不存在")
            }

            // 门店存在且营业中
            val clinic = Clinics.selectAll()
                .where { Clinics.id eq req.clinicId }
                .toList().firstOrNull()
                ?: throw ValidationException("门店不存在")
            if (clinic[Clinics.status] != 1) {
                throw ValidationException("门店当前未营业，无法预约")
            }

            // 医生存在且在职
            val doctor = Doctors.selectAll()
                .where { Doctors.id eq req.doctorId }
                .toList().firstOrNull()
                ?: throw ValidationException("医生不存在")
            if (doctor[Doctors.status] != 1) {
                throw ValidationException("医生已离职，无法预约")
            }

            // 排班匹配：该医生在该星期几启用的排班中，首个覆盖所选开始时间的时段
            val schedule = DoctorSchedules.selectAll()
                .where {
                    (DoctorSchedules.doctorId eq req.doctorId) and
                        (DoctorSchedules.dayOfWeek eq dayOfWeek) and
                        (DoctorSchedules.isEnabled eq 1)
                }
                .toList()
                .firstOrNull { row ->
                    req.startTime in row[DoctorSchedules.startTime]..<row[DoctorSchedules.endTime]
                }
                ?: throw ValidationException("该时段不可预约")

            // 医生该时段容量校验
            val bookedCount = Appointments.selectAll()
                .where {
                    (Appointments.doctorId eq req.doctorId) and
                        (Appointments.appointmentDate eq appointmentDate) and
                        (Appointments.startTime eq req.startTime) and
                        (Appointments.status neq "cancelled")
                }
                .count()
            if (bookedCount >= schedule[DoctorSchedules.maxAppointments]) {
                throw ConflictException("该时段已满")
            }

            // 客户同时段去重
            val userBooked = Appointments.selectAll()
                .where {
                    (Appointments.userId eq req.userId) and
                        (Appointments.appointmentDate eq appointmentDate) and
                        (Appointments.startTime eq req.startTime) and
                        (Appointments.status neq "cancelled")
                }
                .count()
            if (userBooked > 0L) {
                throw ConflictException("该客户在该时段已有预约")
            }

            val endTime = startTimeObj
                .plusMinutes(schedule[DoctorSchedules.slotDuration].toLong())
                .format(formatter)

            Appointments.insert {
                it[Appointments.userId] = req.userId
                it[Appointments.clinicId] = req.clinicId
                it[Appointments.doctorId] = req.doctorId
                it[Appointments.departmentId] = req.departmentId
                it[Appointments.appointmentDate] = appointmentDate
                it[Appointments.startTime] = req.startTime
                it[Appointments.endTime] = endTime
                // 代客预约默认已确认
                it[Appointments.status] = "confirmed"
                it[Appointments.remark] = req.remark
                it[Appointments.updatedAt] = LocalDateTime.now()
            } get Appointments.id
        }
    }

    private fun parseApptDate(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        throw ValidationException("日期格式应为 yyyy-MM-dd，收到：$s")
    }
}

/**
 * 客户管理（Admin 端）
 */
class CustomerService(private val pointsService: AdminPointsService) : BaseService() {

    /**
     * 客户列表。
     *
     * page/pageSize 均为可选：不传时返回全量（下拉选择器等场景需一次拿全），
     * 传了才分页。分页时 total 是符合筛选条件的总行数，而非当页行数。
     */
    suspend fun listCustomers(
        keyword: String?, clinicId: Long?,
        page: Int? = null, pageSize: Int? = null,
        scope: ClinicScope = ClinicScope.ALL,
        tagId: Long? = null,
        memberLevel: Int? = null,
        startDate: String? = null,
        endDate: String? = null
    ): CustomerListResponse = dbQuery {
        // 门店数据隔离：客户归属按首选就诊门店 primaryClinicId 判定
        if (scope.isEmpty) {
            return@dbQuery CustomerListResponse(list = emptyList(), total = 0, page = page, pageSize = pageSize)
        }
        // 标签筛选：先定位打了该标签的 userId 集合（一次查询复用）；标签下无客户时直接空页，避免 IN ()
        val tagUserIds: List<Long>? = if (tagId != null) {
            val ids = UserTagRels.selectAll()
                .where { UserTagRels.tagId eq tagId }
                .toList()
                .map { it[UserTagRels.userId] }
                .distinct()
            if (ids.isEmpty()) {
                return@dbQuery CustomerListResponse(list = emptyList(), total = 0, page = page, pageSize = pageSize)
            }
            ids
        } else null
        
        // P2-18: 会员等级筛选：先定位该等级的 userId 集合
        val levelUserIds: List<Long>? = if (memberLevel != null) {
            val levelId = MemberLevels.selectAll()
                .where { MemberLevels.levelCode eq memberLevel }
                .toList()
                .firstOrNull()?.get(MemberLevels.id)
            if (levelId != null) {
                val ids = UserMemberLevels.selectAll()
                    .where { UserMemberLevels.levelId eq levelId }
                    .toList()
                    .map { it[UserMemberLevels.userId] }
                if (ids.isEmpty()) {
                    return@dbQuery CustomerListResponse(list = emptyList(), total = 0, page = page, pageSize = pageSize)
                }
                ids
            } else null
        } else null
        
        var query = Users.selectAll()
        if (clinicId != null) {
            query = query.andWhere { Users.primaryClinicId eq clinicId }
        }
        if (!scope.all) {
            query = query.andWhere { Users.primaryClinicId inList scope.clinicIds }
        }
        if (tagUserIds != null) {
            query = query.andWhere { Users.id inList tagUserIds }
        }
        if (levelUserIds != null) {
            query = query.andWhere { Users.id inList levelUserIds }
        }
        // P2-18: 注册日期范围筛选
        if (!startDate.isNullOrBlank()) {
            val start = java.time.LocalDate.parse(startDate).atStartOfDay()
            query = query.andWhere { Users.createdAt greaterEq start }
        }
        if (!endDate.isNullOrBlank()) {
            val end = java.time.LocalDate.parse(endDate).atTime(23, 59, 59)
            query = query.andWhere { Users.createdAt lessEq end }
        }
        // keyword 下推到 SQL：原先是「全表捞出再内存过滤」，客户量增长后每翻一页都要扫全表，
        // 且内存过滤使分页无法在数据库层完成（total 与当页数据必须同源）。
        // PostgreSQL 的 LIKE 大小写敏感，而原内存过滤用 lowercase() 不敏感，
        // 故两侧都 lowerCase() 保持原行为，不造成功能退化。
        if (!keyword.isNullOrBlank()) {
            val pattern = "%${keyword.trim().lowercase()}%"
            query = query.andWhere {
                (Users.phone.lowerCase() like pattern) or
                    (Users.nickname.lowerCase() like pattern) or
                    (Users.realName.lowerCase() like pattern)
            }
        }

        val total = query.count()
        var rowsQuery = query.orderBy(Users.createdAt, SortOrder.DESC)
        if (page != null && pageSize != null) {
            rowsQuery = rowsQuery.limit(pageSize).offset(((page - 1) * pageSize).toLong())
        }
        val pagedUsers = rowsQuery.toList()

        // Batch load wallets and member levels
        val userIds = pagedUsers.map { it[Users.id] }
        val walletsMap = if (userIds.isNotEmpty()) {
            UserWallets.selectAll()
                .where { UserWallets.userId inList userIds }
                .toList()
                .associateBy { it[UserWallets.userId] }
        } else emptyMap()

        val umlMap = if (userIds.isNotEmpty()) {
            UserMemberLevels.selectAll()
                .where { UserMemberLevels.userId inList userIds }
                .toList()
                .associateBy { it[UserMemberLevels.userId] }
        } else emptyMap()

        val levelIds = umlMap.values.map { it[UserMemberLevels.levelId] }.distinct()
        val levelsMap = if (levelIds.isNotEmpty()) {
            MemberLevels.selectAll()
                .where { MemberLevels.id inList levelIds }
                .toList()
                .associateBy { it[MemberLevels.id] }
        } else emptyMap()

        // 累计消费：取折后金额（ConsumptionRecords.amount），与营收看板口径一致；
        // 已退款记录不计入。一次查询 + 内存聚合，避免逐客户 N+1。
        val consumptionMap = if (userIds.isNotEmpty()) {
            ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.userId inList userIds) and
                        (ConsumptionRecords.status eq "success")
                }
                .toList()
                .groupBy { it[ConsumptionRecords.userId] }
                .mapValues { (_, records) ->
                    records.sumOf { it[ConsumptionRecords.amount].toDouble() }
                }
        } else emptyMap()

        // 客户标签：两次查询（关系 + 字典）批量装配为 userId -> 标签列表，避免逐客户 N+1。
        // 仅取启用中的标签，与打标校验口径一致，停用标签不在列表/筛选中展示。
        val tagsMap: Map<Long, List<CustomerTagDto>> = if (userIds.isNotEmpty()) {
            val rels = UserTagRels.selectAll()
                .where { UserTagRels.userId inList userIds }
                .toList()
            val relTagIds = rels.map { it[UserTagRels.tagId] }.distinct()
            val tagRows = if (relTagIds.isNotEmpty()) {
                CustomerTags.selectAll()
                    .where { (CustomerTags.id inList relTagIds) and (CustomerTags.status eq 1) }
                    .toList()
            } else emptyList()
            val tagDtoMap = tagRows.associate { row ->
                row[CustomerTags.id] to CustomerTagDto(
                    id = row[CustomerTags.id],
                    tagName = row[CustomerTags.tagName],
                    color = row[CustomerTags.color],
                    remark = row[CustomerTags.remark],
                    status = row[CustomerTags.status]
                )
            }
            rels.mapNotNull { rel ->
                tagDtoMap[rel[UserTagRels.tagId]]?.let { rel[UserTagRels.userId] to it }
            }.groupBy({ it.first }, { it.second })
        } else emptyMap()

        val list = pagedUsers.map { user ->
            val wallet = walletsMap[user[Users.id]]
            val uml = umlMap[user[Users.id]]
            val levelId = uml?.get(UserMemberLevels.levelId)
            val ml = levelId?.let { levelsMap[it] }

            CustomerResponse(
                id = user[Users.id],
                phone = user[Users.phone],
                nickname = user[Users.nickname],
                realName = user[Users.realName],
                gender = user[Users.gender],
                age = user[Users.age],
                avatar = user[Users.avatar],
                primaryClinicId = user[Users.primaryClinicId],
                balance = wallet?.get(UserWallets.balance)?.toDouble() ?: 0.0,
                bonusBalance = wallet?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0,
                memberLevel = ml?.get(MemberLevels.levelCode),
                levelName = ml?.get(MemberLevels.levelName),
                totalConsumption = consumptionMap[user[Users.id]] ?: 0.0,
                tags = tagsMap[user[Users.id]] ?: emptyList(),
                createdAt = user[Users.createdAt].toString()
            )
        }

        // 用可序列化 DTO 包装：原先返回 Map<String, Any>，Any 无序列化器，
        // 会在响应阶段抛 SerializationException 使整个接口 400。
        CustomerListResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    suspend fun getCustomerDetail(id: Long): CustomerDetailResponse = dbQuery {
        val user = Users.selectAll()
            .where { Users.id eq id }
            .toList().firstOrNull() ?: throw ValidationException("客户不存在")

        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq id }
            .toList().firstOrNull()

        val uml = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq id }
            .toList().firstOrNull()

        val levelId = uml?.get(UserMemberLevels.levelId)
        val ml = if (levelId != null) {
            MemberLevels.selectAll()
                .where { MemberLevels.id eq levelId }
                .toList().firstOrNull()
        } else null

        // 客户当前标签（详情页/打标弹窗展示）；仅展示启用中的标签
        val detailTagIds = UserTagRels.selectAll()
            .where { UserTagRels.userId eq id }
            .toList()
            .map { it[UserTagRels.tagId] }
        val detailTags = if (detailTagIds.isNotEmpty()) {
            CustomerTags.selectAll()
                .where { (CustomerTags.id inList detailTagIds) and (CustomerTags.status eq 1) }
                .orderBy(CustomerTags.id, SortOrder.ASC)
                .toList()
                .map { row ->
                    CustomerTagDto(
                        id = row[CustomerTags.id],
                        tagName = row[CustomerTags.tagName],
                        color = row[CustomerTags.color],
                        remark = row[CustomerTags.remark],
                        status = row[CustomerTags.status]
                    )
                }
        } else emptyList()

        CustomerDetailResponse(
            user = CustomerInfoDto(
                id = user[Users.id],
                phone = user[Users.phone],
                nickname = user[Users.nickname],
                realName = user[Users.realName],
                gender = user[Users.gender],
                age = user[Users.age],
                avatar = user[Users.avatar],
                primaryClinicId = user[Users.primaryClinicId],
                tags = detailTags,
                remark = user[Users.remark],
                createdAt = user[Users.createdAt].toString()
            ),
            wallet = CustomerWalletDto(
                balance = wallet?.get(UserWallets.balance)?.toDouble() ?: 0.0,
                bonusBalance = wallet?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0,
                version = wallet?.get(UserWallets.version) ?: 0
            ),
            memberLevel = CustomerLevelDto(
                levelCode = ml?.get(MemberLevels.levelCode),
                levelName = ml?.get(MemberLevels.levelName),
                minConsumption = ml?.get(MemberLevels.minConsumption)?.toDouble() ?: 0.0,
                discount = ml?.get(MemberLevels.discountRate)?.toDouble() ?: 1.0
            )
        )
    }

    /**
     * 管理端人工调整客户余额。
     *
     * 三项此前缺失的保障：
     * - 乐观锁：与充值、消费扣款一致地按 version 更新，避免并发调整互相覆盖丢失其中一笔；
     * - reason 必填并落流水：资金调整必须留痕。不能只依赖 admin_audit_log，
     *   它会被 AdminCleanupService 清理 90 天前的记录，而账目需长期可追溯；
     * - 余额不可为负：扣减额超过现有余额时直接拒绝，而不是把钱包调成负数。
     *
     * 调整只作用于本金余额（balance），赠送余额（bonusBalance）由充值赠送与推荐奖励产生，
     * 不开放人工修改，避免绕过营销规则凭空造赠送金额。
     *
     * @param amount 正数=入账，负数=出账
     * @param operator 操作员（管理端账号），写入资金流水
     */
    suspend fun adjustWallet(
        userId: Long,
        amount: Double,
        reason: String,
        operator: String
    ): WalletAdjustResult = dbQuery {
        if (reason.isBlank()) {
            throw ValidationException("调整原因不能为空")
        }
        val change = amount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
        if (change == BigDecimal.ZERO.setScale(2)) {
            throw ValidationException("调整金额不能为0")
        }

        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList().firstOrNull()

        // 钱包缺失时允许管理端直接开户，但只能入账：没有钱包等于余额为 0，扣减必然为负
        if (wallet == null) {
            if (change < BigDecimal.ZERO) {
                throw ValidationException("客户尚无钱包（余额为0），无法扣减 ${change.negate()} 元")
            }
            UserWallets.insert {
                it[UserWallets.userId] = userId
                it[balance] = change
                it[bonusBalance] = BigDecimal.ZERO
                it[version] = 0
                it[updatedAt] = LocalDateTime.now()
            }
            recordWalletTransaction(
                userId = userId,
                changeType = "adjust",
                amount = change,
                balanceBefore = BigDecimal.ZERO,
                balanceAfter = change,
                reason = reason.take(200),
                operatorName = operator
            )
            return@dbQuery WalletAdjustResult(
                balance = change.toDouble(),
                bonusBalance = 0.0,
                version = 0
            )
        }

        val balanceBefore = wallet[UserWallets.balance]
        val bonusBefore = wallet[UserWallets.bonusBalance]
        val balanceAfter = balanceBefore.add(change)
        if (balanceAfter < BigDecimal.ZERO) {
            throw ValidationException("调整后余额不能为负：当前 $balanceBefore 元，本次调整 $change 元")
        }

        val currentVersion = wallet[UserWallets.version]
        val updated = UserWallets.update({
            (UserWallets.userId eq userId) and (UserWallets.version eq currentVersion)
        }) {
            it[UserWallets.balance] = balanceAfter
            it[UserWallets.version] = currentVersion + 1
            // 该列只有 defaultExpression，不显式赋值则更新时不会刷新
            it[UserWallets.updatedAt] = LocalDateTime.now()
        }
        if (updated == 0) {
            throw ConflictException("客户余额已被其他操作变更，请重新提交")
        }

        recordWalletTransaction(
            userId = userId,
            changeType = "adjust",
            amount = change,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            bonusBefore = bonusBefore,
            bonusAfter = bonusBefore,
            reason = reason.take(200),
            operatorName = operator
        )

        WalletAdjustResult(
            balance = balanceAfter.toDouble(),
            bonusBalance = bonusBefore.toDouble(),
            version = currentVersion + 1
        )
    }

    suspend fun adjustLevel(userId: Long, levelCode: Int, reason: String): Unit = dbQuery {
        if (reason.isBlank()) {
            throw ValidationException("调整原因不能为空")
        }
        val target = MemberLevels.selectAll()
            .where { MemberLevels.levelCode eq levelCode }
            .toList().firstOrNull()
            ?: throw ValidationException("无效的会员等级代码")

        val existing = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq userId }
            .toList().firstOrNull()

        // 调整前的等级编码：无关联视为新客户建档（null），用于判定升降级方向
        val oldLevelCode = existing?.let { row ->
            MemberLevels.selectAll()
                .where { MemberLevels.id eq row[UserMemberLevels.levelId] }
                .toList().firstOrNull()
                ?.get(MemberLevels.levelCode)
        }

        if (existing == null) {
            UserMemberLevels.insert {
                it[UserMemberLevels.userId] = userId
                it[UserMemberLevels.levelId] = target[MemberLevels.id]
                it[UserMemberLevels.effectiveFrom] = LocalDateTime.now()
            }
        } else {
            UserMemberLevels.update({ UserMemberLevels.userId eq userId }) {
                it[UserMemberLevels.levelId] = target[MemberLevels.id]
                it[UserMemberLevels.effectiveFrom] = LocalDateTime.now()
            }
        }

        // 等级未变化时不下发通知，避免无意义打扰
        if (oldLevelCode == levelCode) return@dbQuery

        val upgraded = oldLevelCode == null || levelCode > oldLevelCode
        val noticeContent = buildLevelAdjustNotice(
            levelName = target[MemberLevels.levelName],
            discountRate = target[MemberLevels.discountRate],
            upgraded = upgraded,
            reason = reason
        )
        ScheduledNotifier.notify(
            userId = userId,
            templateKey = "level_change",
            title = if (upgraded) "会员等级提升" else "会员等级调整",
            content = noticeContent,
            subscribeData = mapOf(
                "thing1" to target[MemberLevels.levelName],
                "thing2" to if (upgraded) "等级提升" else "等级调整"
            ),
            page = "/pages/profile/index",
            msgType = "system"
        )
    }

    /** 手动调整等级通知正文（需求 3.6.1）：新等级 + 诊疗折扣 + 调整原因。 */
    private fun buildLevelAdjustNotice(
        levelName: String,
        discountRate: BigDecimal,
        upgraded: Boolean,
        reason: String
    ): String {
        val discount = discountRate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()
        val verb = if (upgraded) "提升" else "调整"
        return "您的会员等级已${verb}为「$levelName」，可享 $discount 折诊疗折扣。" +
            "调整原因：${reason.take(100)}。"
    }

    suspend fun adjustPoints(userId: Long, req: PointsAdjustRequest, operator: String) {
        if (req.reason.isBlank()) {
            throw ValidationException("调整原因不能为空")
        }
        // Delegate to AdminPointsService for the actual points operation
        pointsService.adjustUserPoints(userId, req.amount, req.reason, operator)
    }

    // P2-18: 更新客户备注
    suspend fun updateRemark(userId: Long, remark: String?) = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[Users.remark] = remark
            it[Users.updatedAt] = LocalDateTime.now()
        }
    }
}
