package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AdminDashboardService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction(DBConfig.database) { block() }

    /**
     * 实时概览：今日预约数、到店数、营收、新增客户
     */
    suspend fun getSummary(): DashboardSummary = dbQuery {
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay()
        val todayEnd = today.plusDays(1).atStartOfDay()

        // 今日预约数
        val todayAppointments = Appointments.selectAll()
            .where {
                (Appointments.createdAt greaterEq todayStart) and
                (Appointments.createdAt less todayEnd)
            }
            .count().toInt()

        // 今日到店数
        val todayArrivals = Appointments.selectAll()
            .where {
                (Appointments.arrivedAt greaterEq todayStart) and
                (Appointments.arrivedAt less todayEnd)
            }
            .count().toInt()

        // 今日营收（消费记录）
        val todayRevenue = ConsumptionRecords.selectAll()
            .where {
                (ConsumptionRecords.createdAt greaterEq todayStart) and
                (ConsumptionRecords.createdAt less todayEnd)
            }
            .toList()
            .sumOf { it[ConsumptionRecords.amount].toDouble() }

        // 今日新增客户
        val todayNewCustomers = Users.selectAll()
            .where {
                (Users.createdAt greaterEq todayStart) and
                (Users.createdAt less todayEnd)
            }
            .count().toInt()

        DashboardSummary(
            todayAppointments = todayAppointments,
            todayArrivals = todayArrivals,
            todayRevenue = todayRevenue,
            todayNewCustomers = todayNewCustomers
        )
    }

    /**
     * 门店对比数据
     */
    suspend fun getClinicComparison(): List<ClinicComparison> = dbQuery {
        val clinics = Clinics.selectAll().toList()

        clinics.map { clinicRow ->
            val clinicId = clinicRow[Clinics.id]

            // 本月预约量
            val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()
            val appointmentCount = Appointments.selectAll()
                .where {
                    (Appointments.clinicId eq clinicId) and
                    (Appointments.createdAt greaterEq monthStart)
                }
                .count().toInt()

            // 本月营收
            val revenue = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.clinicId eq clinicId) and
                    (ConsumptionRecords.createdAt greaterEq monthStart)
                }
                .toList()
                .sumOf { it[ConsumptionRecords.amount].toDouble() }

            // 本月新客数
            val newCustomerCount = Users.selectAll()
                .where {
                    (Users.primaryClinicId eq clinicId) and
                    (Users.createdAt greaterEq monthStart)
                }
                .count().toInt()

            ClinicComparison(
                clinicId = clinicId,
                clinicName = clinicRow[Clinics.name],
                appointmentCount = appointmentCount,
                revenue = revenue,
                newCustomerCount = newCustomerCount,
                customerSatisfaction = null
            )
        }
    }

    /**
     * 趋势数据：近N天的预约、营收、新客趋势
     */
    suspend fun getTrends(days: Int = 30): List<TrendDataPoint> = dbQuery {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        (0 until days).map { i ->
            val date = startDate.plusDays(i.toLong())
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()

            val appointmentCount = Appointments.selectAll()
                .where {
                    (Appointments.createdAt greaterEq dayStart) and
                    (Appointments.createdAt less dayEnd)
                }
                .count().toInt()

            val revenue = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.createdAt greaterEq dayStart) and
                    (ConsumptionRecords.createdAt less dayEnd)
                }
                .toList()
                .sumOf { it[ConsumptionRecords.amount].toDouble() }

            val newCustomerCount = Users.selectAll()
                .where {
                    (Users.createdAt greaterEq dayStart) and
                    (Users.createdAt less dayEnd)
                }
                .count().toInt()

            TrendDataPoint(
                date = date.format(formatter),
                appointmentCount = appointmentCount,
                revenue = revenue,
                newCustomerCount = newCustomerCount
            )
        }
    }

    /**
     * 客户等级分布
     */
    suspend fun getCustomerLevelDistribution(): List<CustomerLevelDistribution> = dbQuery {
        val levels = MemberLevels.selectAll().toList()

        levels.map { levelRow ->
            val levelId = levelRow[MemberLevels.id]
            val count = UserMemberLevels.selectAll()
                .where { UserMemberLevels.levelId eq levelId }
                .count().toInt()

            CustomerLevelDistribution(
                levelCode = levelRow[MemberLevels.levelCode],
                levelName = levelRow[MemberLevels.levelName],
                count = count
            )
        }
    }

    /**
     * 客户来源分布
     */
    suspend fun getCustomerSourceDistribution(): List<CustomerSourceDistribution> = dbQuery {
        val totalUsers = Users.selectAll().count().toInt()
        if (totalUsers == 0) return@dbQuery emptyList()

        val sourceTypes = listOf("self" to "自主注册", "referral" to "推荐注册", "store_entry" to "门店录入")

        sourceTypes.map { (sourceType, label) ->
            val count = Users.selectAll()
                .where { Users.sourceType eq sourceType }
                .count().toInt()

            CustomerSourceDistribution(
                sourceType = sourceType,
                sourceLabel = label,
                count = count,
                percentage = if (totalUsers > 0) count.toDouble() / totalUsers * 100 else 0.0
            )
        }
    }

    /**
     * 推荐统计数据
     */
    suspend fun getReferralStats(): ReferralStats = dbQuery {
        // 推荐转化率 = 完成首次消费的被推荐人 / 总被推荐人
        val totalReferees = ReferralRelations.selectAll().count().toInt()
        val convertedReferees = ReferralRelations.selectAll()
            .where { ReferralRelations.firstConsumedAt.isNotNull() }
            .count().toInt()
        val conversionRate = if (totalReferees > 0) convertedReferees.toDouble() / totalReferees * 100 else 0.0

        // 推荐人TOP10 — 简化实现：统计每个推荐人的被推荐人数
        val allRelations = ReferralRelations.selectAll().toList()
        val referrerCounts = allRelations.groupBy { it[ReferralRelations.referrerId] }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(10)

        val topReferrers = referrerCounts.map { (referrerId, count) ->
            val userRow = Users.selectAll()
                .where { Users.id eq referrerId }
                .toList()
                .singleOrNull()

            TopReferrer(
                userId = referrerId,
                nickname = userRow?.get(Users.nickname),
                referralCount = count
            )
        }

        // 月度推荐新客数和营收
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()
        val monthlyNewCustomers = ReferralRelations.selectAll()
            .where { ReferralRelations.registeredAt greaterEq monthStart }
            .count().toInt()

        // 推荐带来的营收（通过被推荐人的消费记录）
        val referredUserIds = ReferralRelations.selectAll()
            .where { ReferralRelations.firstConsumedAt greaterEq monthStart }
            .toList()
            .map { it[ReferralRelations.refereeId] }

        val monthlyRevenue = if (referredUserIds.isNotEmpty()) {
            ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.userId inList referredUserIds) and
                    (ConsumptionRecords.createdAt greaterEq monthStart)
                }
                .toList()
                .sumOf { it[ConsumptionRecords.amount].toDouble() }
        } else 0.0

        ReferralStats(
            conversionRate = conversionRate,
            topReferrers = topReferrers,
            monthlyNewCustomers = monthlyNewCustomers,
            monthlyRevenue = monthlyRevenue
        )
    }

    /**
     * 客户留存指标：复诊率、流失率
     */
    suspend fun getCustomerRetention(): CustomerRetention = dbQuery {
        val totalCustomers = Users.selectAll().count().toInt()
        if (totalCustomers == 0) return@dbQuery CustomerRetention(0.0, 0.0)

        // 复诊率 = 有2次及以上就诊记录的客户 / 总客户
        val allCompletedAppointments = Appointments.selectAll()
            .where { Appointments.status eq "completed" }
            .toList()
        val userAppointmentCounts = allCompletedAppointments.groupBy { it[Appointments.userId] }
            .mapValues { it.value.size }
        val repeatVisitCustomers = userAppointmentCounts.count { it.value >= 2 }

        val repeatVisitRate = if (totalCustomers > 0) repeatVisitCustomers.toDouble() / totalCustomers * 100 else 0.0

        // 流失率 = 90天无活跃的客户 / 总客户
        val ninetyDaysAgo = LocalDate.now().minusDays(90).atStartOfDay()
        val activeCustomers = Users.selectAll()
            .where { Users.lastLoginAt greaterEq ninetyDaysAgo }
            .count().toInt()
        val churnedCustomers = totalCustomers - activeCustomers
        val churnRate = if (totalCustomers > 0) churnedCustomers.toDouble() / totalCustomers * 100 else 0.0

        CustomerRetention(
            repeatVisitRate = repeatVisitRate,
            churnRate = churnRate
        )
    }
}
