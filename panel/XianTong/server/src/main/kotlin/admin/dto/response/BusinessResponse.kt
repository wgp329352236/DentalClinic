package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

// ==================== 基础数据 Response ====================

@Serializable
data class ClinicResponse(
    val id: Long, val name: String, val address: String, val phone: String,
    val openTime: String, val closeTime: String,
    val description: String?, val coverImage: String?,
    val status: Int, val sortOrder: Int
)

@Serializable
data class DepartmentResponse(
    val id: Long, val clinicId: Long, val deptName: String, val description: String?
)

@Serializable
data class DoctorResponse(
    val id: Long, val name: String, val title: String?, val specialties: String?,
    val introduction: String?, val avatar: String?, val clinicIds: List<Long>
)

@Serializable
data class ScheduleResponse(
    val id: Long, val doctorId: Long, val dayOfWeek: Int,
    val startTime: String, val endTime: String,
    val maxAppointments: Int, val isEnabled: Int
)

// ==================== 基础数据 Request ====================

@Serializable
data class ClinicRequest(
    val name: String, val address: String, val phone: String,
    val openTime: String, val closeTime: String,
    val description: String? = null, val coverImage: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    val status: Int = 1, val sortOrder: Int = 0
)

@Serializable
data class DepartmentRequest(val clinicId: Long, val name: String, val description: String? = null)

@Serializable
data class DoctorRequest(
    val name: String, val title: String, val specialties: String,
    val introduction: String? = null, val avatar: String? = null,
    val clinicIds: List<Long> = emptyList(),
    val status: Int = 1 // 1=在职 0=离职
)

@Serializable
data class ScheduleRequest(
    val doctorId: Long, val dayOfWeek: Int,
    val startTime: String, val endTime: String,
    val maxAppointments: Int = 1,
    val slotDuration: Int = 30
)

// ==================== 预约管理 ====================

@Serializable
data class AppointmentAdminResponse(
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

@Serializable
data class AppointmentStatusRequest(val status: String, val remark: String? = null)

/**
 * 管理端代客预约请求（前台/客服替客户下单）。
 * 不经过 C 端爽约黑名单，初始状态直接为 confirmed（到院仍需正常签到流转）。
 */
@Serializable
data class AdminCreateAppointmentRequest(
    val userId: Long,
    val clinicId: Long,
    val doctorId: Long,
    val departmentId: Long? = null,
    /** yyyy-MM-dd */
    val appointmentDate: String,
    /** HH:mm，必须落在医生当天启用排班的某个时段内 */
    val startTime: String,
    val remark: String? = null
)

@Serializable
data class AppointmentDetailResponse(
    val appointment: AppointmentDetailDto,
    val consumption: ConsumptionSummaryDto
)

@Serializable
data class AppointmentDetailDto(
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

@Serializable
data class ConsumptionSummaryDto(
    val totalAmount: Double,
    val recordCount: Int
)

@Serializable
data class MedicalRecordUpdateRequest(val diagnosis: String?, val treatmentPlan: String?)

// ==================== 客户管理 ====================

@Serializable
data class CustomerResponse(
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
    val tags: List<CustomerTagDto> = emptyList(),
    val createdAt: String
)

@Serializable
data class AppointmentListResponse(
    val list: List<AppointmentAdminResponse>,
    val total: Long,
    /** 当前页码；为 null 表示本次返回的是全量（未传分页参数） */
    val page: Int? = null,
    val pageSize: Int? = null
)

@Serializable
data class CustomerListResponse(
    val list: List<CustomerResponse>,
    val total: Long,
    /** 当前页码；为 null 表示本次返回的是全量（未传分页参数） */
    val page: Int? = null,
    val pageSize: Int? = null
)

@Serializable
data class CustomerDetailResponse(
    val user: CustomerInfoDto,
    val wallet: CustomerWalletDto,
    val memberLevel: CustomerLevelDto
)

@Serializable
data class CustomerInfoDto(
    val id: Long,
    val phone: String?,
    val nickname: String?,
    val realName: String?,
    val gender: Int?,
    val age: Int?,
    val avatar: String?,
    val primaryClinicId: Long?,
    val tags: List<CustomerTagDto> = emptyList(),
    val remark: String? = null,
    val createdAt: String
)

@Serializable
data class CustomerWalletDto(
    val balance: Double,
    val bonusBalance: Double,
    val version: Int
)

@Serializable
data class CustomerLevelDto(
    val levelCode: Int?,
    val levelName: String?,
    val minConsumption: Double,
    val discount: Double
)

@Serializable
data class WalletAdjustRequest(
    /** 正数=入账，负数=出账；仅作用于本金余额 */
    val amount: Double,
    /** 调整原因，必填，写入 wallet_transaction 供长期追溯 */
    val reason: String
)

/** 余额调整结果：回传调整后的钱包状态，便于前端就地刷新而无需重拉详情。 */
@Serializable
data class WalletAdjustResult(
    val balance: Double,
    val bonusBalance: Double,
    val version: Int
)

@Serializable
data class LevelAdjustRequest(val levelCode: Int, val reason: String)

@Serializable
data class RemarkUpdateRequest(val remark: String?)

// PointsAdjustRequest is defined in AdminPointsService.kt (com.dentalclinic.admin.service)
