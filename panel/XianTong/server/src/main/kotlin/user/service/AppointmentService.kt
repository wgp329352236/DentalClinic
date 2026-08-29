package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Appointment service for booking management.
 */
class AppointmentService {

    /**
     * Get all active clinics.
     */
    suspend fun getClinics(): List<ClinicDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Get departments for a clinic.
     */
    suspend fun getDepartments(clinicId: Long): List<DepartmentDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Get doctors available at a clinic, optionally filtered by department.
     */
    suspend fun getDoctors(clinicId: Long, departmentId: Long?): List<DoctorDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            // Get doctor IDs associated with this clinic
            val doctorIds = DoctorClinicRels.selectAll()
                .where { DoctorClinicRels.clinicId eq clinicId }
                .toList()
                .map { it[DoctorClinicRels.doctorId] }

            if (doctorIds.isEmpty()) {
                emptyList<DoctorDto>()
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
    }

    /**
     * Get available time slots for a doctor on a specific date.
     * Returns slots with remaining capacity.
     */
    suspend fun getAvailableSlots(doctorId: Long, date: LocalDate): List<SlotDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Create an appointment.
     */
    suspend fun createAppointment(userId: Long, request: CreateAppointmentRequest): AppointmentResult {
        // Parse date string
        val appointmentDate = try {
            LocalDate.parse(request.appointmentDate)
        } catch (e: Exception) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }

        // Pre-validation before entering transaction
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val dayOfWeek = appointmentDate.dayOfWeek.value

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
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
                        request.startTime >= schedStart && request.startTime < schedEnd
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
                            // Calculate end time
                            val startTimeObj = java.time.LocalTime.parse(request.startTime)
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
    }

    /**
     * Get user's appointment list.
     */
    suspend fun getMyAppointments(userId: Long, status: String?): List<MyAppointmentDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val baseQuery = Appointments.selectAll().where { Appointments.userId eq userId }

            val rows = if (status != null && status.isNotEmpty()) {
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
    }

    /**
     * Cancel an appointment.
     */
    suspend fun cancelAppointment(userId: Long, appointmentId: Long): AppointmentResult {
        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
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
                    if (status == "cancelled") {
                        AppointmentResult.Error("预约已取消")
                    } else if (status == "completed") {
                        AppointmentResult.Error("预约已完成，无法取消")
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

    /**
     * Reschedule an appointment to a new date/time.
     */
    suspend fun rescheduleAppointment(userId: Long, appointmentId: Long, newDate: String, newStartTime: String): AppointmentResult {
        // Parse new date
        val appointmentDate = try {
            LocalDate.parse(newDate)
        } catch (e: Exception) {
            return AppointmentResult.Error("日期格式错误，请使用 yyyy-MM-dd")
        }

        // Check if new date is in the past
        if (appointmentDate.isBefore(LocalDate.now())) {
            return AppointmentResult.Error("不能改约到过去的日期")
        }

        // Pre-check appointment exists and status
        val preCheck = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                Appointments.selectAll()
                    .where {
                        (Appointments.id eq appointmentId) and
                        (Appointments.userId eq userId)
                    }
                    .toList()
                    .singleOrNull()
            }
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

        val doctorId = preCheck[Appointments.doctorId]

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
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
                        newStartTime >= schedStart && newStartTime < schedEnd
                    }

                if (schedule == null) {
                    AppointmentResult.Error("该时段不可预约")
                } else {
                    val maxAppointments = schedule[DoctorSchedules.maxAppointments]
                    if (bookedCount >= maxAppointments) {
                        AppointmentResult.Error("该时段已满")
                    } else {
                        // Calculate new end time
                        val startTimeObj = java.time.LocalTime.parse(newStartTime)
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
