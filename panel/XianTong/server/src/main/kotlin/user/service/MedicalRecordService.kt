package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.Departments
import com.dentalclinic.admin.entity.Doctors
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.format.DateTimeFormatter

/**
 * Medical record service for user appointment history.
 */
class MedicalRecordService : BaseService() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
    ): MedicalRecordsResponse = dbQuery {
        // Build query with filters
        var query = Appointments.selectAll()
            .where {
                (Appointments.userId eq userId) and
                (Appointments.status eq "completed")
            }

        if (!startDate.isNullOrBlank()) {
            val start = java.time.LocalDate.parse(startDate)
            query = query.andWhere { Appointments.appointmentDate greaterEq start }
        }
        if (!endDate.isNullOrBlank()) {
            val end = java.time.LocalDate.parse(endDate)
            query = query.andWhere { Appointments.appointmentDate lessEq end }
        }
        if (doctorId != null) {
            query = query.andWhere { Appointments.doctorId eq doctorId }
        }
        if (departmentId != null) {
            query = query.andWhere { Appointments.departmentId eq departmentId }
        }

        val total = query.count()

        val rows = query
            .orderBy(Appointments.appointmentDate, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        // Batch load all related data to avoid N+1 queries
        val clinicIds = rows.map { it[Appointments.clinicId] }.distinct()
        val doctorIds = rows.map { it[Appointments.doctorId] }.distinct()
        val deptIds = rows.mapNotNull { it[Appointments.departmentId] }.distinct()
        val appointmentIds = rows.map { it[Appointments.id] }

        val clinicsMap = batchLoadClinics(clinicIds)
        val doctorsMap = batchLoadDoctors(doctorIds)
        val deptsMap = batchLoadDepartments(deptIds)
        val consumptionMap = batchLoadConsumptionByAppointment(appointmentIds)

        // Filter by keyword in memory (diagnosis, remark, clinic name, doctor name)
        val filteredRows = if (!keyword.isNullOrBlank()) {
            val kw = keyword.lowercase()
            rows.filter { row ->
                val diagnosis = row[Appointments.diagnosis]?.lowercase() ?: ""
                val remark = row[Appointments.remark]?.lowercase() ?: ""
                val clinicName = clinicsMap[row[Appointments.clinicId]]?.lowercase() ?: ""
                val doctorName = doctorsMap[row[Appointments.doctorId]]?.first?.lowercase() ?: ""

                diagnosis.contains(kw) || remark.contains(kw) ||
                clinicName.contains(kw) || doctorName.contains(kw)
            }
        } else {
            rows
        }

        val records = filteredRows.map { row ->
            val clinic = clinicsMap[row[Appointments.clinicId]]
            val (doctorName, doctorTitle) = doctorsMap[row[Appointments.doctorId]] ?: (null to null)
            val deptName = deptsMap[row[Appointments.departmentId]]
            val totalAmount = consumptionMap[row[Appointments.id]] ?: 0.0

            MedicalRecordDto(
                id = row[Appointments.id],
                appointmentDate = row[Appointments.appointmentDate].format(dateFormatter),
                startTime = row[Appointments.startTime],
                endTime = row[Appointments.endTime],
                clinicName = clinic ?: "",
                doctorName = doctorName ?: "",
                doctorTitle = doctorTitle,
                departmentName = deptName,
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

    /**
     * Get detailed medical record by appointment ID.
     */
    suspend fun getMedicalRecordDetail(userId: Long, appointmentId: Long): MedicalRecordDetailDto? = dbQuery {
        val appointmentRow = Appointments.selectAll()
            .where {
                (Appointments.id eq appointmentId) and
                (Appointments.userId eq userId)
            }
            .toList()
            .firstOrNull() ?: return@dbQuery null

        // Batch load related data (single query each)
        val clinicsMap = batchLoadClinics(listOf(appointmentRow[Appointments.clinicId]))
        val doctorsMap = batchLoadDoctors(listOf(appointmentRow[Appointments.doctorId]))
        val deptsMap = batchLoadDepartments(
            appointmentRow[Appointments.departmentId]?.let { listOf(it) } ?: emptyList()
        )

        val clinic = clinicsMap[appointmentRow[Appointments.clinicId]]
        val clinicAddress = if (clinic != null) {
            Clinics.selectAll()
                .where { Clinics.id eq appointmentRow[Appointments.clinicId] }
                .toList().firstOrNull()?.get(Clinics.address)
        } else null

        val (doctorName, doctorTitle) = doctorsMap[appointmentRow[Appointments.doctorId]] ?: (null to null)
        val doctorIntro = if (doctorName != null) {
            Doctors.selectAll()
                .where { Doctors.id eq appointmentRow[Appointments.doctorId] }
                .toList().firstOrNull()?.get(Doctors.introduction)
        } else null

        val deptName = deptsMap[appointmentRow[Appointments.departmentId]]

        // Get all consumption records for this appointment
        val consumptionRows = ConsumptionRecords.selectAll()
            .where { ConsumptionRecords.appointmentId eq appointmentId }
            .toList()

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
            appointmentDate = appointmentRow[Appointments.appointmentDate].format(dateFormatter),
            startTime = appointmentRow[Appointments.startTime],
            endTime = appointmentRow[Appointments.endTime],
            clinicName = clinic ?: "",
            clinicAddress = clinicAddress,
            doctorName = doctorName ?: "",
            doctorTitle = doctorTitle,
            doctorIntroduction = doctorIntro,
            departmentName = deptName,
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

    // ==================== Batch Load Helpers ====================

    /** Returns Map<clinicId, clinicName> */
    private suspend fun batchLoadClinics(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        return Clinics.selectAll()
            .where { Clinics.id inList ids }
            .toList()
            .associate { it[Clinics.id] to it[Clinics.name] }
    }

    /** Returns Map<doctorId, (name, title)> */
    private suspend fun batchLoadDoctors(ids: List<Long>): Map<Long, Pair<String?, String?>> {
        if (ids.isEmpty()) return emptyMap()
        return Doctors.selectAll()
            .where { Doctors.id inList ids }
            .toList()
            .associate { it[Doctors.id] to (it[Doctors.name] to it[Doctors.title]) }
    }

    /** Returns Map<deptId, deptName> */
    private suspend fun batchLoadDepartments(ids: List<Long>): Map<Long?, String?> {
        if (ids.isEmpty()) return emptyMap()
        return Departments.selectAll()
            .where { Departments.id inList ids }
            .toList()
            .associate { it[Departments.id] to it[Departments.name] }
    }

    /** Returns Map<appointmentId, totalConsumptionAmount> */
    private suspend fun batchLoadConsumptionByAppointment(appointmentIds: List<Long>): Map<Long, Double> {
        if (appointmentIds.isEmpty()) return emptyMap()
        return ConsumptionRecords.selectAll()
            .where { ConsumptionRecords.appointmentId inList appointmentIds }
            .toList()
            .filter { it[ConsumptionRecords.appointmentId] != null }
            .groupBy { it[ConsumptionRecords.appointmentId]!! }
            .mapValues { (_, rows) -> rows.sumOf { it[ConsumptionRecords.amount].toDouble() } }
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
