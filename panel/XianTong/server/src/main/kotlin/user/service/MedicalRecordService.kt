package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.Departments
import com.dentalclinic.admin.entity.Doctors
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.format.DateTimeFormatter

/**
 * Medical record service for user appointment history.
 */
class MedicalRecordService {

    /**
     * Get user's medical records (completed appointments) with pagination and filters.
     */
    suspend fun getMedicalRecords(
        userId: Long,
        page: Int,
        pageSize: Int,
        startDate: String? = null,
        endDate: String? = null,
        doctorId: Long? = null,
        departmentId: Long? = null,
        keyword: String? = null
    ): MedicalRecordsResponse = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            // Build query with filters
            var query = Appointments.selectAll()
                .where {
                    (Appointments.userId eq userId) and
                    (Appointments.status eq "completed")
                }

            // Filter by date range
            if (!startDate.isNullOrBlank()) {
                val start = java.time.LocalDate.parse(startDate)
                query = query.where { Appointments.appointmentDate greaterEq start }
            }
            if (!endDate.isNullOrBlank()) {
                val end = java.time.LocalDate.parse(endDate)
                query = query.where { Appointments.appointmentDate lessEq end }
            }

            // Filter by doctor
            if (doctorId != null) {
                query = query.where { Appointments.doctorId eq doctorId }
            }

            // Filter by department
            if (departmentId != null) {
                query = query.where { Appointments.departmentId eq departmentId }
            }

            val total = query.count()

            val rows = query
                .orderBy(Appointments.appointmentDate, SortOrder.DESC)
                .limit(pageSize).offset((page - 1).toLong())
                .toList()

            // Filter by keyword in memory (diagnosis, remark, clinic name, doctor name)
            val filteredRows = if (!keyword.isNullOrBlank()) {
                val kw = keyword.lowercase()
                rows.filter { row ->
                    val diagnosis = row[Appointments.diagnosis]?.lowercase() ?: ""
                    val remark = row[Appointments.remark]?.lowercase() ?: ""
                    
                    // Get clinic and doctor names for keyword search
                    val clinicName = Clinics.selectAll()
                        .where { Clinics.id eq row[Appointments.clinicId] }
                        .toList()
                        .firstOrNull()
                        ?.get(Clinics.name)?.lowercase() ?: ""
                    
                    val doctorName = Doctors.selectAll()
                        .where { Doctors.id eq row[Appointments.doctorId] }
                        .toList()
                        .firstOrNull()
                        ?.get(Doctors.name)?.lowercase() ?: ""
                    
                    diagnosis.contains(kw) || remark.contains(kw) || 
                    clinicName.contains(kw) || doctorName.contains(kw)
                }
            } else {
                rows
            }

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            val records = filteredRows.map { row ->
                // Get clinic info
                val clinicRows = Clinics.selectAll()
                    .where { Clinics.id eq row[Appointments.clinicId] }
                    .toList()
                val clinic = clinicRows.firstOrNull()

                // Get doctor info
                val doctorRows = Doctors.selectAll()
                    .where { Doctors.id eq row[Appointments.doctorId] }
                    .toList()
                val doctor = doctorRows.firstOrNull()

                // Get department info
                val deptId = row[Appointments.departmentId]
                val dept = if (deptId != null) {
                    val deptRows = Departments.selectAll()
                        .where { Departments.id eq deptId }
                        .toList()
                    deptRows.firstOrNull()
                } else null

                // Get consumption amount for this appointment
                val consumptionRows = ConsumptionRecords.selectAll()
                    .where { ConsumptionRecords.appointmentId eq row[Appointments.id] }
                    .toList()
                val totalAmount = consumptionRows.sumOf { it[ConsumptionRecords.amount].toDouble() }

                MedicalRecordDto(
                    id = row[Appointments.id],
                    appointmentDate = row[Appointments.appointmentDate].format(formatter),
                    startTime = row[Appointments.startTime],
                    endTime = row[Appointments.endTime],
                    clinicName = clinic?.get(Clinics.name) ?: "",
                    doctorName = doctor?.get(Doctors.name) ?: "",
                    doctorTitle = doctor?.get(Doctors.title),
                    departmentName = dept?.get(Departments.name),
                    diagnosis = row[Appointments.diagnosis],
                    remark = row[Appointments.remark],
                    consumptionAmount = totalAmount,
                    completedAt = row[Appointments.completedAt]?.toString()
                )
            }

            MedicalRecordsResponse(
                list = records,
                total = total,
                page = page,
                pageSize = pageSize
            )
        }
    }

    /**
     * Get detailed medical record by appointment ID.
     */
    suspend fun getMedicalRecordDetail(userId: Long, appointmentId: Long): MedicalRecordDetailDto? = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val appointmentRow = Appointments.selectAll()
                .where {
                    (Appointments.id eq appointmentId) and
                    (Appointments.userId eq userId)
                }
                .toList()
                .firstOrNull() ?: return@suspendTransaction null

            // Get clinic info
            val clinicRows = Clinics.selectAll()
                .where { Clinics.id eq appointmentRow[Appointments.clinicId] }
                .toList()
            val clinic = clinicRows.firstOrNull()

            // Get doctor info
            val doctorRows = Doctors.selectAll()
                .where { Doctors.id eq appointmentRow[Appointments.doctorId] }
                .toList()
            val doctor = doctorRows.firstOrNull()

            // Get department info
            val deptId = appointmentRow[Appointments.departmentId]
            val dept = if (deptId != null) {
                val deptRows = Departments.selectAll()
                    .where { Departments.id eq deptId }
                    .toList()
                deptRows.firstOrNull()
            } else null

            // Get all consumption records for this appointment
            val consumptionRows = ConsumptionRecords.selectAll()
                .where { ConsumptionRecords.appointmentId eq appointmentId }
                .toList()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            val consumptionDetails = consumptionRows.map { row ->
                ConsumptionDetailDto(
                    id = row[ConsumptionRecords.id],
                    amount = row[ConsumptionRecords.amount].toDouble(),
                    balanceUsed = row[ConsumptionRecords.balanceUsed].toDouble(),
                    wechatPaid = row[ConsumptionRecords.wechatPaid].toDouble(),
                    pointsEarned = row[ConsumptionRecords.pointsEarned],
                    description = row[ConsumptionRecords.description],
                    createdAt = row[ConsumptionRecords.createdAt].format(dateTimeFormatter)
                )
            }

            val totalAmount = consumptionRows.sumOf { it[ConsumptionRecords.amount].toDouble() }

            MedicalRecordDetailDto(
                id = appointmentRow[Appointments.id],
                appointmentDate = appointmentRow[Appointments.appointmentDate].format(formatter),
                startTime = appointmentRow[Appointments.startTime],
                endTime = appointmentRow[Appointments.endTime],
                clinicName = clinic?.get(Clinics.name) ?: "",
                clinicAddress = clinic?.get(Clinics.address),
                doctorName = doctor?.get(Doctors.name) ?: "",
                doctorTitle = doctor?.get(Doctors.title),
                doctorIntroduction = doctor?.get(Doctors.introduction),
                departmentName = dept?.get(Departments.name),
                status = appointmentRow[Appointments.status],
                diagnosis = appointmentRow[Appointments.diagnosis],
                treatmentPlan = appointmentRow[Appointments.treatmentPlan],
                remark = appointmentRow[Appointments.remark],
                arrivedAt = appointmentRow[Appointments.arrivedAt]?.format(dateTimeFormatter),
                completedAt = appointmentRow[Appointments.completedAt]?.format(dateTimeFormatter),
                totalAmount = totalAmount,
                consumptionDetails = consumptionDetails
            )
        }
    }
}

// DTOs

@Serializable
data class MedicalRecordDto(
    val id: Long,
    val appointmentDate: String,
    val startTime: String?,
    val endTime: String?,
    val clinicName: String,
    val doctorName: String,
    val doctorTitle: String?,
    val departmentName: String?,
    val diagnosis: String?,
    val remark: String?,
    val consumptionAmount: Double,
    val completedAt: String?
)

@Serializable
data class MedicalRecordsResponse(
    val list: List<MedicalRecordDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class MedicalRecordDetailDto(
    val id: Long,
    val appointmentDate: String,
    val startTime: String?,
    val endTime: String?,
    val clinicName: String,
    val clinicAddress: String?,
    val doctorName: String,
    val doctorTitle: String?,
    val doctorIntroduction: String?,
    val departmentName: String?,
    val status: String,
    val diagnosis: String?,
    val treatmentPlan: String?,
    val remark: String?,
    val arrivedAt: String?,
    val completedAt: String?,
    val totalAmount: Double,
    val consumptionDetails: List<ConsumptionDetailDto>
)

@Serializable
data class ConsumptionDetailDto(
    val id: Long,
    val amount: Double,
    val balanceUsed: Double,
    val wechatPaid: Double,
    val pointsEarned: Int,
    val description: String?,
    val createdAt: String
)
