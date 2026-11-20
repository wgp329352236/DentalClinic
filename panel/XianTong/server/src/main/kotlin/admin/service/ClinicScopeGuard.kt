package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.ClinicScope
import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.Departments
import com.dentalclinic.admin.entity.DoctorClinicRels
import com.dentalclinic.admin.entity.DoctorSchedules
import com.dentalclinic.admin.entity.Doctors
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.util.ForbiddenException
import com.dentalclinic.admin.util.NotFoundException
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.selectAll

/**
 * 门店数据范围判断的唯一口径。
 *
 * 全部门店权限直接放行；受限账号只允许命中明确授权门店。注意 null 门店不属于任何范围，
 * 对受限账号按“不可见/不可操作”处理，避免历史脏数据成为越权入口。
 */
fun ClinicScope.allowsClinic(clinicId: Long?): Boolean =
    all || (clinicId != null && clinicId in clinicIds)

/**
 * 管理员账号、角色权限和门店授权属于全局系统配置，只能由全部门店范围账号写入。
 * 否则受限管理员可给自己补权，绕过所有业务数据隔离。
 */
fun requireFullClinicScope(scope: ClinicScope, message: String) {
    if (!scope.all) throw ForbiddenException(message)
}

/**
 * 校验门店存在且属于当前门店范围。必须在数据库事务内调用。
 */
suspend fun requireClinicInScope(clinicId: Long, scope: ClinicScope): ResultRow {
    val clinic = Clinics.selectAll()
        .where { Clinics.id eq clinicId }
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("门诊不存在")

    if (!scope.allowsClinic(clinicId)) {
        throw ForbiddenException("无权访问该门店")
    }
    return clinic
}

/**
 * 批量校验门店均存在且全部处于当前范围内，供医生门店关系写入前使用。
 */
suspend fun requireClinicsInScope(clinicIds: Collection<Long>, scope: ClinicScope) {
    val distinctIds = clinicIds.distinct()
    if (distinctIds.isEmpty()) return

    val existingIds = Clinics.selectAll()
        .where { Clinics.id inList distinctIds }
        .toList()
        .map { it[Clinics.id] }
        .toSet()
    if (existingIds.size != distinctIds.size) {
        throw NotFoundException("门诊不存在")
    }
    if (!scope.all && distinctIds.any { it !in scope.clinicIds }) {
        throw ForbiddenException("无权操作未授权门店")
    }
}

/**
 * 校验客户属于当前门店范围并返回用户行。
 *
 * 客户归属口径与客户列表一致：user.primary_clinic_id。必须在数据库事务内调用。
 */
suspend fun requireUserInScope(userId: Long, scope: ClinicScope): ResultRow {
    val user = Users.selectAll()
        .where { Users.id eq userId }
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("客户不存在")

    if (!scope.allowsClinic(user[Users.primaryClinicId])) {
        throw ForbiddenException("无权访问该门店的客户")
    }
    return user
}

/**
 * 校验预约属于当前门店范围并返回预约行。必须在数据库事务内调用。
 *
 * @param lockForUpdate 为 true 时对预约行加锁，供审批、取消、改约等状态变更串行执行。
 */
suspend fun requireAppointmentInScope(
    appointmentId: Long,
    scope: ClinicScope,
    lockForUpdate: Boolean = false
): ResultRow {
    val query = Appointments.selectAll()
        .where { Appointments.id eq appointmentId }
    val appointment = (if (lockForUpdate) query.forUpdate() else query)
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("预约不存在")

    if (!scope.allowsClinic(appointment[Appointments.clinicId])) {
        throw ForbiddenException("无权访问该门店的预约")
    }
    return appointment
}

/**
 * 校验科室属于当前门店范围并返回科室行。必须在数据库事务内调用。
 */
suspend fun requireDepartmentInScope(departmentId: Long, scope: ClinicScope): ResultRow {
    val department = Departments.selectAll()
        .where { Departments.id eq departmentId }
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("科室不存在")

    if (!scope.allowsClinic(department[Departments.clinicId])) {
        throw ForbiddenException("无权访问该门店的科室")
    }
    return department
}

/**
 * 校验医生属于当前门店范围并返回医生行。必须在数据库事务内调用。
 *
 * 医生可跨门店共享，而医生资料和排班表没有 clinic_id。默认要求该医生全部关联门店
 * 都在当前范围内，避免单店管理员修改后影响其它门店；requireAllClinics=false 仅用于
 * 安全的纯展示场景。
 */
suspend fun requireDoctorInScope(
    doctorId: Long,
    scope: ClinicScope,
    requireAllClinics: Boolean = true
): ResultRow {
    val doctor = Doctors.selectAll()
        .where { Doctors.id eq doctorId }
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("医生不存在")

    if (scope.all) return doctor

    val clinicIds = DoctorClinicRels.selectAll()
        .where { DoctorClinicRels.doctorId eq doctorId }
        .toList()
        .map { it[DoctorClinicRels.clinicId] }
    val allowed = if (requireAllClinics) {
        clinicIds.isNotEmpty() && clinicIds.all { scope.allowsClinic(it) }
    } else {
        clinicIds.any { scope.allowsClinic(it) }
    }
    if (!allowed) {
        throw ForbiddenException("无权访问该医生的跨门店资料或排班")
    }
    return doctor
}

/**
 * 排班没有 clinic_id，通过医生-门店关系推导数据权限。必须在数据库事务内调用。
 */
suspend fun requireScheduleInScope(scheduleId: Long, scope: ClinicScope): ResultRow {
    val schedule = DoctorSchedules.selectAll()
        .where { DoctorSchedules.id eq scheduleId }
        .toList()
        .singleOrNull()
        ?: throw NotFoundException("排班不存在")
    requireDoctorInScope(schedule[DoctorSchedules.doctorId], scope)
    return schedule
}

/**
 * 受限账号可见客户 ID。调用方必须先排除 scope.all，避免超大 IN 查询。
 */
suspend fun restrictedScopedUserIds(scope: ClinicScope): List<Long> {
    if (scope.all || scope.clinicIds.isEmpty()) return emptyList()
    return Users.selectAll()
        .where { Users.primaryClinicId inList scope.clinicIds }
        .toList()
        .map { it[Users.id] }
}
