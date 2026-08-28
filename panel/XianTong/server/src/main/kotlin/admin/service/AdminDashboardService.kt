package com.dentalclinic.admin.service

import com.dentalclinic.admin.dto.response.*

/**
 * Dashboard service — placeholder for dental clinic metrics.
 * TODO: Implement with actual clinic/appointment/customer data.
 */
class AdminDashboardService {

    suspend fun getSummary(): DashboardSummary = DashboardSummary(
        totalGames = 0,
        pulledToday = 0,
        pushedToday = 0,
        successRate = 1.0
    )

    suspend fun getGameDataVolumes(): List<GameDataVolume> = emptyList()

    suspend fun getTrends(): List<TrendDataPoint> = emptyList()

    suspend fun getChannelDistribution(gameCode: String): List<ChannelDistribution> = emptyList()
}
