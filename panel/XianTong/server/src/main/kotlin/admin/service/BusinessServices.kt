package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.math.RoundingMode
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

    /**
     * 预约列表。
     *
     * page/pageSize 均为可选：不传时返回全量（下拉选择器等场景需一次拿全），
     * 传了才分页。分页时 total 是符合筛选条件的总行数，而非当页行数。
     */
    suspend fun listAppointments(
        status: String?, clinicId: Long?, doctorId: Long?, userId: Long? = null,
        page: Int? = null, pageSize: Int? = null
    ): AppointmentListResponse = dbQuery {
        var query = Appointments.selectAll()
        if (!status.isNullOrBlank()) {
            query = query.andWhere { Appointments.status eq status }
        }
        if (clinicId != null) {
            query = query.andWhere { Appointments.clinicId eq clinicId }
        }
        if (doctorId != null) {
            query = query.andWhere { Appointments.doctorId eq doctorId }
        }
        // 按客户过滤：供「录入消费」弹窗联动该客户的预约
        if (userId != null) {
            query = query.andWhere { Appointments.userId eq userId }
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
class CustomerService(private val pointsService: AdminPointsService) : BaseService() {

    /**
     * 客户列表。
     *
     * page/pageSize 均为可选：不传时返回全量（下拉选择器等场景需一次拿全），
     * 传了才分页。分页时 total 是符合筛选条件的总行数，而非当页行数。
     */
    suspend fun listCustomers(
        keyword: String?, clinicId: Long?,
        page: Int? = null, pageSize: Int? = null
    ): CustomerListResponse = dbQuery {
        var query = Users.selectAll()
        if (clinicId != null) {
            query = query.andWhere { Users.primaryClinicId eq clinicId }
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

    suspend fun adjustPoints(userId: Long, req: PointsAdjustRequest, operator: String) {
        if (req.reason.isBlank()) {
            throw ValidationException("调整原因不能为空")
        }
        // Delegate to AdminPointsService for the actual points operation
        pointsService.adjustUserPoints(userId, req.amount, req.reason, operator)
    }
}
