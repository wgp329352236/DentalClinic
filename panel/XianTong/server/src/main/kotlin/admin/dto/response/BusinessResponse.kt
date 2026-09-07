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
    val status: Int = 1, val sortOrder: Int = 0
)

@Serializable
data class DepartmentRequest(val clinicId: Long, val name: String, val description: String? = null)

@Serializable
data class DoctorRequest(
    val name: String, val title: String, val specialties: String,
    val introduction: String? = null, val avatar: String? = null,
    val clinicIds: List<Long> = emptyList()
)

@Serializable
data class ScheduleRequest(
    val doctorId: Long, val dayOfWeek: Int,
    val startTime: String, val endTime: String,
    val maxAppointments: Int = 1
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
    val createdAt: String
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
data class WalletAdjustRequest(val amount: Double, val reason: String)

@Serializable
data class LevelAdjustRequest(val levelCode: Int, val reason: String)

// PointsAdjustRequest is defined in AdminPointsService.kt (com.dentalclinic.admin.service)
