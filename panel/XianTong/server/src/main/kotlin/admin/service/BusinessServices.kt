package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ValidationException
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime

/**
 * 门诊管理
 */
class ClinicService : BaseService() {

    suspend fun listClinics(): List<ClinicResponse> = dbQuery {
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

    suspend fun createClinic(req: ClinicRequest): Long = dbQuery {
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

    suspend fun updateClinic(id: Long, req: ClinicRequest): Unit = dbQuery {
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

    suspend fun listAppointments(
        status: String?, clinicId: Long?, doctorId: Long?
    ): Map<String, Any> = dbQuery {
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

        mapOf("list" to list, "total" to list.size)
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
        Appointments.update({ Appointments.id eq id }) {
            it[status] = "cancelled"
            it[cancelReason] = reason
            it[cancelledAt] = LocalDateTime.now()
        }
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
}

/**
 * 客户管理（Admin 端）
 */
class CustomerService : BaseService() {

    suspend fun listCustomers(
        keyword: String?, clinicId: Long?
    ): Map<String, Any> = dbQuery {
        var query = Users.selectAll()
        if (clinicId != null) {
            query = query.where { Users.primaryClinicId eq clinicId }
        }
        val allUsers = query
            .orderBy(Users.createdAt, SortOrder.DESC)
            .toList()

        // Filter by keyword in memory (R2DBC doesn't support like)
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

        // Batch load wallets and member levels
        val userIds = filteredUsers.map { it[Users.id] }
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

        val levelIds = umlMap.values.mapNotNull { it[UserMemberLevels.levelId] }.distinct()
        val levelsMap = if (levelIds.isNotEmpty()) {
            MemberLevels.selectAll()
                .where { MemberLevels.id inList levelIds }
                .toList()
                .associateBy { it[MemberLevels.id] }
        } else emptyMap()

        val list = filteredUsers.map { user ->
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
                totalConsumption = 0.0, // TODO: calculate from consumption records
                createdAt = user[Users.createdAt].toString()
            )
        }

        mapOf("list" to list, "total" to list.size)
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

    suspend fun adjustWallet(userId: Long, amount: Double): Unit = dbQuery {
        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList().firstOrNull()

        if (wallet == null) {
            UserWallets.insert {
                it[UserWallets.userId] = userId
                it[balance] = amount.toBigDecimal()
                it[bonusBalance] = 0.toBigDecimal()
                it[version] = 1
            }
        } else {
            UserWallets.update({ UserWallets.userId eq userId }) {
                it[balance] = wallet[UserWallets.balance] + amount.toBigDecimal()
            }
        }
    }

    suspend fun adjustLevel(userId: Long, levelCode: Int): Unit = dbQuery {
        val level = MemberLevels.selectAll()
            .where { MemberLevels.levelCode eq levelCode }
            .toList().firstOrNull()
        val targetLevelId = level?.get(MemberLevels.id)
            ?: throw ValidationException("无效的会员等级代码")

        val existing = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq userId }
            .toList().firstOrNull()

        if (existing == null) {
            UserMemberLevels.insert {
                it[UserMemberLevels.userId] = userId
                it[UserMemberLevels.levelId] = targetLevelId
                it[UserMemberLevels.effectiveFrom] = LocalDateTime.now()
            }
        } else {
            UserMemberLevels.update({ UserMemberLevels.userId eq userId }) {
                it[UserMemberLevels.levelId] = targetLevelId
                it[UserMemberLevels.effectiveFrom] = LocalDateTime.now()
            }
        }
    }

    suspend fun adjustPoints(userId: Long, req: PointsAdjustRequest, operator: String): Unit = dbQuery {
        if (req.reason.isBlank()) {
            throw ValidationException("调整原因不能为空")
        }

        // Get current balance
        var currentBalance = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

        // If no summary exists, calculate from records
        if (currentBalance == 0) {
            currentBalance = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .toList()
                .sumOf { it[PointsRecords.changeAmount] }
        }

        val newBalance = currentBalance + req.amount
        if (newBalance < 0) {
            throw ValidationException("积分余额不足，当前余额：$currentBalance")
        }

        val expiresAt = LocalDateTime.now().plusMonths(12)

        // Insert points record
        PointsRecords.insert {
            it[PointsRecords.userId] = userId
            it[PointsRecords.changeAmount] = req.amount
            it[PointsRecords.balanceAfter] = newBalance
            it[PointsRecords.sourceType] = "admin_adjust"
            it[PointsRecords.description] = "管理员手动调整：${req.reason}（操作人：$operator）"
            it[PointsRecords.expiresAt] = expiresAt
        }

        // Update or insert summary
        val summaryExists = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .count() > 0

        if (summaryExists) {
            UserPointsSummary.update({ UserPointsSummary.userId eq userId }) {
                it[UserPointsSummary.totalBalance] = newBalance
            }
        } else {
            UserPointsSummary.insert {
                it[UserPointsSummary.userId] = userId
                it[UserPointsSummary.totalBalance] = newBalance
            }
        }
    }
}
