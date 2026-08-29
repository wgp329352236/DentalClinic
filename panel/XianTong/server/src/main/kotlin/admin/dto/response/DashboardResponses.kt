package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

/**
 * Dashboard summary — real-time metrics for today.
 */
@Serializable
data class DashboardSummary(
    val todayAppointments: Int,       // 今日预约数
    val todayArrivals: Int,           // 今日到店数
    val todayRevenue: Double,         // 今日营收
    val todayNewCustomers: Int        // 今日新增客户数
)

/**
 * Clinic comparison data.
 */
@Serializable
data class ClinicComparison(
    val clinicId: Long,
    val clinicName: String,
    val appointmentCount: Int,        // 预约量
    val revenue: Double,              // 营收
    val newCustomerCount: Int,        // 新客数
    val customerSatisfaction: Double? // 客户满意度（预留）
)

/**
 * Trend data point for line charts.
 */
@Serializable
data class TrendDataPoint(
    val date: String,                 // yyyy-MM-dd
    val appointmentCount: Int,        // 预约数
    val revenue: Double,              // 营收
    val newCustomerCount: Int         // 新客数
)

/**
 * Customer distribution by member level.
 */
@Serializable
data class CustomerLevelDistribution(
    val levelCode: Int,
    val levelName: String,
    val count: Int
)

/**
 * Customer source distribution.
 */
@Serializable
data class CustomerSourceDistribution(
    val sourceType: String,           // self/referral/store_entry
    val sourceLabel: String,          // 自主注册/推荐注册/门店录入
    val count: Int,
    val percentage: Double            // 百分比 0-100
)

/**
 * Referral statistics.
 */
@Serializable
data class ReferralStats(
    val conversionRate: Double,       // 推荐注册转化率 0-100
    val topReferrers: List<TopReferrer>,
    val monthlyNewCustomers: Int,     // 推荐带来的月度新客数
    val monthlyRevenue: Double        // 推荐带来的月度营收
)

@Serializable
data class TopReferrer(
    val userId: Long,
    val nickname: String?,
    val referralCount: Int
)

/**
 * Customer retention metrics.
 */
@Serializable
data class CustomerRetention(
    val repeatVisitRate: Double,      // 复诊率 0-100
    val churnRate: Double             // 流失率 0-100
)
