package com.dentalclinic.common

import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime

open class BaseService {

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction(DBConfig.database) { block() }

    /**
     * Add points to a user: read current balance, insert record, update/create summary.
     * Shared across all services that need to adjust user points.
     * 
     * NOTE: This is a plain suspend function that operates within the caller's transaction.
     * Callers should ensure they are within a transaction context (dbQuery block).
     *
     * @return the new balance after adjustment
     */
    suspend fun addPointsToUser(
        userId: Long,
        amount: Int,
        sourceType: String,
        description: String,
        validityMonths: Int = 12
    ): Int {
        var currentBalance = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

        if (currentBalance == 0) {
            currentBalance = PointsRecords.selectAll()
                .where { PointsRecords.userId eq userId }
                .toList()
                .sumOf { it[PointsRecords.changeAmount] }
        }

        val newBalance = currentBalance + amount
        val expiresAt = LocalDateTime.now().plusMonths(validityMonths.toLong())

        PointsRecords.insert {
            it[PointsRecords.userId] = userId
            it[PointsRecords.changeAmount] = amount
            it[PointsRecords.balanceAfter] = newBalance
            it[PointsRecords.sourceType] = sourceType
            it[PointsRecords.description] = description
            it[PointsRecords.expiresAt] = expiresAt
        }

        val summaryExists = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .count() > 0

        if (summaryExists) {
            UserPointsSummary.update({ UserPointsSummary.userId eq userId }) {
                it[UserPointsSummary.totalBalance] = newBalance
            }
        } else {
            UserPointsSummary.insert {
                it[UserPointsSummary.userId] = userId
                it[UserPointsSummary.totalBalance] = newBalance
            }
        }

        return newBalance
    }
}
