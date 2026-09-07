package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.config.DBConfig
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.format.DateTimeFormatter

/**
 * User wallet routes (protected by JWT).
 */
fun Application.userWalletRoutes() {
    routing {
        route("/api/v1/wallet") {
            /**
             * Get wallet details including balance and recent recharge records.
             * GET /api/v1/wallet/details
             * Header: Authorization: Bearer <access_token>
             */
            get("/details") {
                val userId = authenticate(call) ?: return@get

                val walletData = suspendTransaction(DBConfig.database) {
                    // Get wallet info
                    val walletRow = UserWallets.selectAll()
                        .where { UserWallets.userId eq userId }
                        .toList()
                        .singleOrNull()

                    // Get recent recharge records (last 10)
                    val rechargeRows = RechargeRecords.selectAll()
                        .where { RechargeRecords.userId eq userId }
                        .orderBy(RechargeRecords.createdAt, SortOrder.DESC)
                        .limit(10)
                        .toList()

                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

                    WalletDetailsDto(
                        balance = walletRow?.get(UserWallets.balance)?.toDouble() ?: 0.0,
                        bonusBalance = walletRow?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0,
                        totalBalance = (walletRow?.get(UserWallets.balance) ?: 0.0.toBigDecimal())
                            .add(walletRow?.get(UserWallets.bonusBalance) ?: 0.0.toBigDecimal())
                            .toDouble(),
                        version = walletRow?.get(UserWallets.version) ?: 0,
                        rechargeHistory = rechargeRows.map { row ->
                            RechargeRecordDto(
                                id = row[RechargeRecords.id],
                                amount = row[RechargeRecords.amount].toDouble(),
                                bonusAmount = row[RechargeRecords.bonusAmount].toDouble(),
                                status = row[RechargeRecords.status],
                                paidAt = row[RechargeRecords.paidAt]?.format(formatter),
                                createdAt = row[RechargeRecords.createdAt].format(formatter)
                            )
                        }
                    )
                }

                call.respond(ApiResponse(code = 0, message = "success", data = walletData))
            }
        }
    }
}

@Serializable
data class WalletDetailsDto(
    val balance: Double,
    val bonusBalance: Double,
    val totalBalance: Double,
    val version: Int,
    val rechargeHistory: List<RechargeRecordDto>
)

@Serializable
data class RechargeRecordDto(
    val id: Long,
    val amount: Double,
    val bonusAmount: Double,
    val status: String,
    val paidAt: String?,
    val createdAt: String
)
