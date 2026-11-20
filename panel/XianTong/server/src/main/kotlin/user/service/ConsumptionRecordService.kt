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
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Consumption record service for user spending history.
 */
class ConsumptionRecordService : BaseService() {

    /**
     * Get user's consumption records with pagination and filters.
     */
    suspend fun getConsumptionRecords(
        userId: Long,
        page: Int,
        pageSize: Int,
        startDate: String? = null,
        endDate: String? = null,
        clinicId: Long? = null
    ): ConsumptionRecordsResponse = dbQuery {
        // Build query with filters
        var query = ConsumptionRecords.selectAll()
            .where { ConsumptionRecords.userId eq userId }

        // Filter by date range
        if (!startDate.isNullOrBlank()) {
            val start = LocalDate.parse(startDate).atStartOfDay()
            query = query.andWhere { ConsumptionRecords.createdAt greaterEq start }
        }
        if (!endDate.isNullOrBlank()) {
            val end = LocalDate.parse(endDate).atTime(23, 59, 59)
            query = query.andWhere { ConsumptionRecords.createdAt lessEq end }
        }

        // Filter by clinic
        if (clinicId != null) {
            query = query.andWhere { ConsumptionRecords.clinicId eq clinicId }
        }

        val total = query.count()

        val rows = query
            .orderBy(ConsumptionRecords.createdAt, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val records = rows.map { row ->
            // Get appointment info if exists
            val appointmentId = row[ConsumptionRecords.appointmentId]
            var appointmentDate: String? = null
            var doctorName: String? = null
            var departmentName: String? = null

            if (appointmentId != null) {
                val appt = Appointments.selectAll()
                    .where { Appointments.id eq appointmentId }
                    .toList()
                    .firstOrNull()
                
                if (appt != null) {
                    appointmentDate = appt[Appointments.appointmentDate].toString()
                    
                    // Get doctor name
                    doctorName = Doctors.selectAll()
                        .where { Doctors.id eq appt[Appointments.doctorId] }
                        .toList()
                        .firstOrNull()?.get(Doctors.name)
                    
                    // Get department name
                    val deptId = appt[Appointments.departmentId]
                    if (deptId != null) {
                        departmentName = Departments.selectAll()
                            .where { Departments.id eq deptId }
                            .toList()
                            .firstOrNull()?.get(Departments.name)
                    }
                }
            }

            // Get clinic name
            val clinicName = Clinics.selectAll()
                .where { Clinics.id eq row[ConsumptionRecords.clinicId] }
                .toList()
                .firstOrNull()?.get(Clinics.name)

            ConsumptionRecordDto(
                id = row[ConsumptionRecords.id],
                amount = row[ConsumptionRecords.amount].toDouble(),
                balanceUsed = row[ConsumptionRecords.balanceUsed].toDouble(),
                wechatPaid = row[ConsumptionRecords.wechatPaid].toDouble(),
                pointsEarned = row[ConsumptionRecords.pointsEarned],
                pointsUsed = row[ConsumptionRecords.pointsUsed],
                pointsDeduct = row[ConsumptionRecords.pointsDeduct].toDouble(),
                paymentStatus = row[ConsumptionRecords.paymentStatus],
                description = row[ConsumptionRecords.description],
                clinicName = clinicName,
                appointmentDate = appointmentDate,
                doctorName = doctorName,
                departmentName = departmentName,
                createdAt = row[ConsumptionRecords.createdAt].format(formatter)
            )
        }

        ConsumptionRecordsResponse(
            list = records,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    /**
     * Get consumption statistics for user.
     */
    suspend fun getConsumptionStatistics(userId: Long): ConsumptionStatisticsDto = dbQuery {
        val now = java.time.LocalDateTime.now()
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        
        // Total consumption
        // 统计只计已支付完成的消费；待微信尾款记录不计入累计/月度消费。
        val allRecords = ConsumptionRecords.selectAll()
            .where {
                (ConsumptionRecords.userId eq userId) and
                    (ConsumptionRecords.status eq "success") and
                    (ConsumptionRecords.paymentStatus eq "success")
            }
            .toList()
        val totalAmount = allRecords.sumOf { it[ConsumptionRecords.amount].toDouble() }
        
        // This month consumption
        val monthRecords = allRecords.filter { 
            it[ConsumptionRecords.createdAt] >= monthStart 
        }
        val monthAmount = monthRecords.sumOf { it[ConsumptionRecords.amount].toDouble() }
        
        // Count
        val totalCount = allRecords.size
        val monthCount = monthRecords.size

        ConsumptionStatisticsDto(
            totalAmount = totalAmount,
            totalRecords = totalCount,
            monthAmount = monthAmount,
            monthRecords = monthCount
        )
    }
}

// DTOs

@Serializable
data class ConsumptionRecordDto(
    val id: Long,
    val amount: Double,
    val balanceUsed: Double,
    val wechatPaid: Double,
    val pointsEarned: Int,
    val pointsUsed: Int,
    val pointsDeduct: Double,
    val paymentStatus: String,
    val description: String?,
    val clinicName: String?,
    val appointmentDate: String?,
    val doctorName: String?,
    val departmentName: String?,
    val createdAt: String
)

@Serializable
data class ConsumptionRecordsResponse(
    val list: List<ConsumptionRecordDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class ConsumptionStatisticsDto(
    val totalAmount: Double,
    val totalRecords: Int,
    val monthAmount: Double,
    val monthRecords: Int
)
