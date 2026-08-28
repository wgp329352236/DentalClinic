package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummary(
    val totalGames: Int,
    val pulledToday: Long,
    val pushedToday: Long,
    val successRate: Double // 0.0 ~ 1.0
)

@Serializable
data class GameDataVolume(
    val gameCode: String,
    val gameName: String,
    val pullCount7d: Long,
    val pushCount7d: Long,
    val lastPullTime: String? = null,
    val lastPushTime: String? = null,
    val stale: Boolean = false // 超过 24h 无新数据
)

@Serializable
data class TrendDataPoint(
    val date: String, // yyyy-MM-dd
    val pullCount: Long,
    val pushCount: Long
)

@Serializable
data class ChannelDistribution(
    val channel: String,
    val count: Long
)

@Serializable
data class PushRecordItem(
    val id: Long,
    val gameCode: String,
    val pushTime: String,
    val targetPlatform: String,
    val recordCount: Int,
    val status: String, // success, partial, failed
    val durationMs: Long? = null,
    val errorDetails: String? = null
)
