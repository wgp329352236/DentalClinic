package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.config.DBConfig
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

@Serializable data class ClinicRequest(val name: String, val address: String, val phone: String, val openTime: String, val closeTime: String, val description: String? = null, val coverImage: String? = null, val status: Int = 1, val sortOrder: Int = 0)
@Serializable data class DepartmentRequest(val clinicId: Long, val name: String, val description: String? = null)
@Serializable data class DoctorRequest(val name: String, val title: String, val specialties: String, val introduction: String? = null, val avatar: String? = null, val clinicIds: List<Long> = emptyList())
@Serializable data class ScheduleRequest(val doctorId: Long, val dayOfWeek: Int, val startTime: String, val endTime: String, val maxAppointments: Int = 1)
@Serializable data class MedicalRecordUpdateRequest(val diagnosis: String?, val treatmentPlan: String?)

// DTO for admin appointment detail view
@Serializable data class AppointmentDetailResponse(
    val appointment: AppointmentDetailDto,
    val consumption: ConsumptionSummaryDto
)

@Serializable data class AppointmentDetailDto(
    val id: Long,
    val userId: Long,
    val userName: String?,
    val userPhone: String?,
    val clinicId: Long,
    val clinicName: String?,
    val doctorId: Long,
    val doctorName: String?,
    val departmentId: Long?,
    val departmentName: String?,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String?,
    val status: String,
    val diagnosis: String?,
    val treatmentPlan: String?,
    val remark: String?,
    val arrivedAt: String?,
    val completedAt: String?,
    val createdAt: String
)

@Serializable data class ConsumptionSummaryDto(
    val totalAmount: Double,
    val recordCount: Int
)

// Helper function to get first element from list (R2DBC doesn't have firstOrNull)
private fun <T> List<T>.firstOrNone(): T? = if (isEmpty()) null else this[0]

fun Route.clinicRoutes() {
    route("/clinics") {
        authenticated {
            requirePermission("clinic:list") {
                get {
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.selectAll()
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
                    }
                    call.ok(data)
                }
            }
            requirePermission("clinic:create") {
                post {
                    val req = call.receive<ClinicRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.insert {
                                it[name] = req.name
                                it[address] = req.address
                                it[phone] = req.phone
                                it[openTime] = req.openTime
                                it[closeTime] = req.closeTime
                                it[description] = req.description
                                it[coverImage] = req.coverImage
                                it[status] = req.status
                                it[sortOrder] = req.sortOrder
                            } get Clinics.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("clinic:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<ClinicRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.update({ Clinics.id eq id }) {
                                it[name] = req.name
                                it[address] = req.address
                                it[phone] = req.phone
                                it[openTime] = req.openTime
                                it[closeTime] = req.closeTime
                                it[description] = req.description
                                it[coverImage] = req.coverImage
                                it[status] = req.status
                                it[sortOrder] = req.sortOrder
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("clinic:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.deleteWhere { Clinics.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.departmentRoutes() {
    route("/departments") {
        authenticated {
            requirePermission("department:list") {
                get {
                    val cid = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val rows = if (cid != null) {
                                Departments.selectAll()
                                    .where { (Departments.status eq 1) and (Departments.clinicId eq cid) }
                                    .toList()
                            } else {
                                Departments.selectAll()
                                    .where { Departments.status eq 1 }
                                    .toList()
                            }
                            rows.map { r ->
                                DepartmentResponse(
                                    id = r[Departments.id],
                                    clinicId = r[Departments.clinicId],
                                    deptName = r[Departments.name],
                                    description = r[Departments.description]
                                )
                            }
                        }
                    }
                    call.ok(data)
                }
            }
            requirePermission("department:create") {
                post {
                    val req = call.receive<DepartmentRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.insert {
                                it[clinicId] = req.clinicId
                                it[name] = req.name
                                it[description] = req.description
                                it[status] = 1
                            } get Departments.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("department:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DepartmentRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.update({ Departments.id eq id }) {
                                it[clinicId] = req.clinicId
                                it[name] = req.name
                                it[description] = req.description
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("department:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.deleteWhere { Departments.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.doctorRoutes() {
    route("/doctors") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Doctors.selectAll().where { Doctors.status eq 1 }.toList().map { row ->
                                val cids = DoctorClinicRels.selectAll()
                                    .where { DoctorClinicRels.doctorId eq row[Doctors.id] }
                                    .toList()
                                    .map { it[DoctorClinicRels.clinicId] }
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
                    }
                    call.ok(data)
                }
            }
            requirePermission("doctor:create") {
                post {
                    val req = call.receive<DoctorRequest>()
                    val did = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
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
                    }
                    call.ok(mapOf("id" to did), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DoctorRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Doctors.update({ Doctors.id eq id }) {
                                it[name] = req.name
                                it[title] = req.title
                                it[specialties] = req.specialties
                                it[introduction] = req.introduction
                                it[avatar] = req.avatar
                            }
                            DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
                            for (c in req.clinicIds) {
                                DoctorClinicRels.insert {
                                    it[doctorId] = id
                                    it[clinicId] = c
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("doctor:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
                            Doctors.deleteWhere { Doctors.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.scheduleRoutes() {
    route("/schedules") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val did = call.request.queryParameters["doctorId"]?.toLongOrNull()
                        ?: throw ValidationException("缺少医生ID")
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.selectAll()
                                .where { DoctorSchedules.doctorId eq did }
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
                    }
                    call.ok(data)
                }
            }
            requirePermission("doctor:update") {
                post {
                    val req = call.receive<ScheduleRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.insert {
                                it[doctorId] = req.doctorId
                                it[dayOfWeek] = req.dayOfWeek
                                it[startTime] = req.startTime
                                it[endTime] = req.endTime
                                it[maxAppointments] = req.maxAppointments
                                it[isEnabled] = 1
                            } get DoctorSchedules.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.deleteWhere { DoctorSchedules.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 预约管理 ====================

@Serializable data class AppointmentAdminResponse(
    val id: Long,
    val userId: Long,
    val userName: String?,
    val userPhone: String?,
    val clinicId: Long,
    val clinicName: String,
    val doctorId: Long,
    val doctorName: String,
    val departmentId: Long?,
    val departmentName: String?,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String?,
    val status: String,
    val remark: String?,
    val createdAt: String
)

@Serializable data class AppointmentStatusRequest(val status: String, val remark: String? = null)

fun Route.appointmentAdminRoutes() {
    route("/appointments") {
        authenticated {
            requirePermission("appointment:list") {
                get {
                    val status = call.request.queryParameters["status"]
                    val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val doctorId = call.request.queryParameters["doctorId"]?.toLongOrNull()

                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            var query = Appointments.selectAll()

                            if (!status.isNullOrBlank()) {
                                query = query.where { Appointments.status eq status }
                            }
                            if (clinicId != null) {
                                query = query.where { Appointments.clinicId eq clinicId }
                            }
                            if (doctorId != null) {
                                query = query.where { Appointments.doctorId eq doctorId }
                            }

                            val allRows = query
                                .orderBy(Appointments.createdAt, SortOrder.DESC)
                                .toList()

                            val list = allRows.map { row ->
                                // Get user info
                                val userRows = Users.selectAll()
                                    .where { Users.id eq row[Appointments.userId] }
                                    .toList()
                                val user = userRows.firstOrNone()

                                // Get clinic info
                                val clinicRows = Clinics.selectAll()
                                    .where { Clinics.id eq row[Appointments.clinicId] }
                                    .toList()
                                val clinic = clinicRows.firstOrNone()

                                // Get doctor info
                                val doctorRows = Doctors.selectAll()
                                    .where { Doctors.id eq row[Appointments.doctorId] }
                                    .toList()
                                val doctor = doctorRows.firstOrNone()

                                // Get department info
                                val deptId = row[Appointments.departmentId]
                                val dept = if (deptId != null) {
                                    val deptRows = Departments.selectAll()
                                        .where { Departments.id eq deptId }
                                        .toList()
                                    deptRows.firstOrNone()
                                } else null

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

                            mapOf("list" to list, "total" to list.size)
                        }
                    }
                    call.ok(data)
                }
            }

            requirePermission("appointment:update") {
                put("/{id}/status") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<AppointmentStatusRequest>()

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Appointments.update({ Appointments.id eq id }) {
                                it[status] = req.status
                                if (req.remark != null) {
                                    it[remark] = req.remark
                                }
                                when (req.status) {
                                    "arrived" -> it[arrivedAt] = java.time.LocalDateTime.now()
                                    "completed" -> it[completedAt] = java.time.LocalDateTime.now()
                                    "cancelled" -> {
                                        it[cancelledAt] = java.time.LocalDateTime.now()
                                    }
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "状态更新成功")
                }
            }

            requirePermission("appointment:update") {
                put("/{id}/cancel") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<Map<String, String?>>()
                    val reason = req["reason"]

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Appointments.update({ Appointments.id eq id }) {
                                it[status] = "cancelled"
                                it[cancelReason] = reason
                                it[cancelledAt] = java.time.LocalDateTime.now()
                            }
                        }
                    }
                    call.ok<Unit>(message = "预约已取消")
                }
            }

            // GET /api/admin/appointments/{id} - 查看预约详情（含病历）
            requirePermission("appointment:view") {
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")

                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val appointmentRows = Appointments.selectAll()
                                .where { Appointments.id eq id }
                                .toList()
                            val appointment = appointmentRows.firstOrNone()
                                ?: throw ValidationException("预约不存在")

                            // Get user info
                            val userRows = Users.selectAll()
                                .where { Users.id eq appointment[Appointments.userId] }
                                .toList()
                            val user = userRows.firstOrNone()

                            // Get clinic info
                            val clinicRows = Clinics.selectAll()
                                .where { Clinics.id eq appointment[Appointments.clinicId] }
                                .toList()
                            val clinic = clinicRows.firstOrNone()

                            // Get doctor info
                            val doctorRows = Doctors.selectAll()
                                .where { Doctors.id eq appointment[Appointments.doctorId] }
                                .toList()
                            val doctor = doctorRows.firstOrNone()

                            // Get department info
                            val deptId = appointment[Appointments.departmentId]
                            val dept = if (deptId != null) {
                                val deptRows = Departments.selectAll()
                                    .where { Departments.id eq deptId }
                                    .toList()
                                deptRows.firstOrNone()
                            } else null

                            // Get consumption records
                            val consumptionRows = ConsumptionRecords.selectAll()
                                .where { ConsumptionRecords.appointmentId eq id }
                                .toList()
                            val totalAmount = consumptionRows.sumOf { it[ConsumptionRecords.amount].toDouble() }

                            val response = AppointmentDetailResponse(
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
                                    totalAmount = totalAmount,
                                    recordCount = consumptionRows.size
                                )
                            )
                            
                            call.ok(response)
                        }
                    }
                }
            }

            // PUT /api/admin/appointments/{id}/medical-record - 编辑病历摘要
            requirePermission("appointment:update") {
                put("/{id}/medical-record") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<MedicalRecordUpdateRequest>()

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Appointments.update({ Appointments.id eq id }) {
                                if (req.diagnosis != null) {
                                    it[diagnosis] = req.diagnosis
                                }
                                if (req.treatmentPlan != null) {
                                    it[treatmentPlan] = req.treatmentPlan
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "病历更新成功")
                }
            }
        }
    }
}

// ==================== 客户管理 ====================

@Serializable data class CustomerResponse(
    val id: Long,
    val phone: String?,
    val nickname: String?,
    val realName: String?,
    val gender: Int?,
    val age: Int?,
    val avatar: String?,
    val primaryClinicId: Long?,
    val balance: Double,
    val bonusBalance: Double,
    val memberLevel: Int?,
    val levelName: String?,
    val totalConsumption: Double,
    val createdAt: String
)

@Serializable data class WalletAdjustRequest(val amount: Double, val reason: String)
@Serializable data class LevelAdjustRequest(val levelCode: Int, val reason: String)
@Serializable data class PointsAdjustRequest(val amount: Int, val reason: String)

fun Route.customerRoutes() {
    route("/customers") {
        authenticated {
            requirePermission("customer:list") {
                get {
                    val keyword = call.request.queryParameters["keyword"]
                    val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()

                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            var query = Users.selectAll()

                            // Filter by clinic
                            if (clinicId != null) {
                                query = query.where { Users.primaryClinicId eq clinicId }
                            }

                            val allUsers = query
                                .orderBy(Users.createdAt, SortOrder.DESC)
                                .toList()

                            // Filter by keyword in memory (since R2DBC doesn't support like)
                            val filteredUsers = if (!keyword.isNullOrBlank()) {
                                val kw = keyword.lowercase()
                                allUsers.filter { user ->
                                    val phone = user[Users.phone]?.lowercase() ?: ""
                                    val nickname = user[Users.nickname]?.lowercase() ?: ""
                                    val realName = user[Users.realName]?.lowercase() ?: ""
                                    phone.contains(kw) || nickname.contains(kw) || realName.contains(kw)
                                }
                            } else {
                                allUsers
                            }

                            val list = filteredUsers.map { user ->
                                // Get wallet
                                val walletRows = UserWallets.selectAll()
                                    .where { UserWallets.userId eq user[Users.id] }
                                    .toList()
                                val wallet = walletRows.firstOrNone()

                                // Get member level
                                val umlRows = UserMemberLevels.selectAll()
                                    .where { UserMemberLevels.userId eq user[Users.id] }
                                    .toList()
                                val uml = umlRows.firstOrNone()

                                val levelId = uml?.get(UserMemberLevels.levelId)
                                val ml = if (levelId != null) {
                                    val mlRows = MemberLevels.selectAll()
                                        .where { MemberLevels.id eq levelId }
                                        .toList()
                                    mlRows.firstOrNone()
                                } else null

                                val levelCode = ml?.get(MemberLevels.levelCode)

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
                                    memberLevel = levelCode,
                                    levelName = ml?.get(MemberLevels.levelName),
                                    totalConsumption = 0.0, // TODO: calculate from consumption records
                                    createdAt = user[Users.createdAt].toString()
                                )
                            }

                            mapOf("list" to list, "total" to list.size)
                        }
                    }
                    call.ok(data)
                }
            }

            requirePermission("customer:view") {
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")

                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val userRows = Users.selectAll()
                                .where { Users.id eq id }
                                .toList()
                            val user = userRows.firstOrNone() ?: throw ValidationException("客户不存在")

                            val walletRows = UserWallets.selectAll()
                                .where { UserWallets.userId eq id }
                                .toList()
                            val wallet = walletRows.firstOrNone()

                            val umlRows = UserMemberLevels.selectAll()
                                .where { UserMemberLevels.userId eq id }
                                .toList()
                            val uml = umlRows.firstOrNone()

                            val levelId = uml?.get(UserMemberLevels.levelId)
                            val ml = if (levelId != null) {
                                val mlRows = MemberLevels.selectAll()
                                    .where { MemberLevels.id eq levelId }
                                    .toList()
                                mlRows.firstOrNone()
                            } else null
                            val levelCode = ml?.get(MemberLevels.levelCode)

                            mapOf(
                                "user" to mapOf(
                                    "id" to user[Users.id],
                                    "phone" to user[Users.phone],
                                    "nickname" to user[Users.nickname],
                                    "realName" to user[Users.realName],
                                    "gender" to user[Users.gender],
                                    "age" to user[Users.age],
                                    "avatar" to user[Users.avatar],
                                    "primaryClinicId" to user[Users.primaryClinicId],
                                    "createdAt" to user[Users.createdAt].toString()
                                ),
                                "wallet" to mapOf(
                                    "balance" to (wallet?.get(UserWallets.balance)?.toDouble() ?: 0.0),
                                    "bonusBalance" to (wallet?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0),
                                    "version" to (wallet?.get(UserWallets.version) ?: 0)
                                ),
                                "memberLevel" to mapOf(
                                    "levelCode" to levelCode,
                                    "levelName" to (ml?.get(MemberLevels.levelName)),
                                    "minConsumption" to (ml?.get(MemberLevels.minConsumption)?.toDouble() ?: 0.0),
                                    "discount" to (ml?.get(MemberLevels.discountRate)?.toDouble() ?: 1.0)
                                )
                            )
                        }
                    }
                    call.ok(data)
                }
            }

            requirePermission("customer:update") {
                put("/{id}/wallet") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<WalletAdjustRequest>()

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val walletRows = UserWallets.selectAll()
                                .where { UserWallets.userId eq id }
                                .toList()
                            val wallet = walletRows.firstOrNone()

                            if (wallet == null) {
                                UserWallets.insert {
                                    it[userId] = id
                                    it[balance] = req.amount.toBigDecimal()
                                    it[bonusBalance] = 0.toBigDecimal()
                                    it[version] = 1
                                }
                            } else {
                                UserWallets.update({ UserWallets.userId eq id }) {
                                    it[balance] = wallet[UserWallets.balance] + req.amount.toBigDecimal()
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "余额调整成功")
                }
            }

            requirePermission("customer:update") {
                put("/{id}/level") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<LevelAdjustRequest>()

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            // Get the level id from level code
                            val levelRows = MemberLevels.selectAll()
                                .where { MemberLevels.levelCode eq req.levelCode }
                                .toList()
                            val level = levelRows.firstOrNone()
                            val targetLevelId = level?.get(MemberLevels.id) ?: throw ValidationException("无效的会员等级代码")

                            val existingRows = UserMemberLevels.selectAll()
                                .where { UserMemberLevels.userId eq id }
                                .toList()
                            val existing = existingRows.firstOrNone()

                            if (existing == null) {
                                UserMemberLevels.insert {
                                    it[UserMemberLevels.userId] = id
                                    it[UserMemberLevels.levelId] = targetLevelId
                                    it[UserMemberLevels.effectiveFrom] = java.time.LocalDateTime.now()
                                }
                            } else {
                                UserMemberLevels.update({ UserMemberLevels.userId eq id }) {
                                    it[UserMemberLevels.levelId] = targetLevelId
                                    it[UserMemberLevels.effectiveFrom] = java.time.LocalDateTime.now()
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "会员等级调整成功")
                }
            }

            requirePermission("customer:update") {
                put("/{id}/points") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<PointsAdjustRequest>()

                    // Validate input
                    if (req.reason.isBlank()) {
                        throw ValidationException("调整原因不能为空")
                    }

                    // Get admin user for operator info
                    val principal = call.adminPrincipal()

                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            // Get current balance
                            var currentBalance = UserPointsSummary.selectAll()
                                .where { UserPointsSummary.userId eq id }
                                .toList()
                                .singleOrNull()
                                ?.get(UserPointsSummary.totalBalance) ?: 0

                            // If no summary exists, calculate from records
                            if (currentBalance == 0) {
                                currentBalance = PointsRecords.selectAll()
                                    .where { PointsRecords.userId eq id }
                                    .toList()
                                    .sumOf { it[PointsRecords.changeAmount] }
                            }

                            val newBalance = currentBalance + req.amount

                            // Validate: balance cannot be negative
                            if (newBalance < 0) {
                                throw ValidationException("积分余额不足，当前余额：$currentBalance")
                            }

                            // Calculate expiresAt (default 12 months from now)
                            val expiresAt = java.time.LocalDateTime.now().plusMonths(12)

                            // Insert points record
                            PointsRecords.insert {
                                it[PointsRecords.userId] = id
                                it[PointsRecords.changeAmount] = req.amount
                                it[PointsRecords.balanceAfter] = newBalance
                                it[PointsRecords.sourceType] = "admin_adjust"
                                it[PointsRecords.description] = "管理员手动调整：${req.reason}（操作人：${principal.username}）"
                                it[PointsRecords.expiresAt] = expiresAt
                            }

                            // Update or insert summary
                            val summaryExists = UserPointsSummary.selectAll()
                                .where { UserPointsSummary.userId eq id }
                                .count() > 0

                            if (summaryExists) {
                                UserPointsSummary.update({ UserPointsSummary.userId eq id }) {
                                    it[UserPointsSummary.totalBalance] = newBalance
                                }
                            } else {
                                UserPointsSummary.insert {
                                    it[UserPointsSummary.userId] = id
                                    it[UserPointsSummary.totalBalance] = newBalance
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "积分调整成功")
                }
            }
        }
    }
}
