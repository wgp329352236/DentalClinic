package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ClinicResponse(
    val id: Long, val name: String, val address: String, val phone: String,
    val openTime: String, val closeTime: String,
    val description: String?, val coverImage: String?,
    val status: Int, val sortOrder: Int
)

@Serializable
data class DepartmentResponse(
    val id: Long, val clinicId: Long, val name: String, val description: String?
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
