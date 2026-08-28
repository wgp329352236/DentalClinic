package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.config.DBConfig
import com.dentalclinic.user.auth.UserJwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * User profile routes (protected by JWT).
 */
fun Application.userProfileRoutes() {
    routing {
        route("/api/v1/profile") {
            /**
             * Get current user's profile.
             * GET /api/v1/profile/me
             * Header: Authorization: Bearer <access_token>
             */
            get("/me") {
                // Extract and verify token from Authorization header
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@get
                }

                val token = authHeader.removePrefix("Bearer ")
                val decoded = UserJwtConfig.verifyAccessToken(token)
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@get
                }

                val userId = decoded.subject.toLong()

                // Fetch user data from database
                val userData = suspendTransaction(DBConfig.database) {
                    val userRow = Users.selectAll()
                        .where { Users.id eq userId }
                        .toList()
                        .singleOrNull()

                    val walletRow = UserWallets.selectAll()
                        .where { UserWallets.userId eq userId }
                        .toList()
                        .singleOrNull()

                    if (userRow != null) {
                        UserProfileDto(
                            userId = userRow[Users.id],
                            openid = userRow[Users.openid],
                            phone = userRow[Users.phone],
                            nickname = userRow[Users.nickname],
                            realName = userRow[Users.realName],
                            gender = userRow[Users.gender],
                            age = userRow[Users.age],
                            avatar = userRow[Users.avatar],
                            primaryClinicId = userRow[Users.primaryClinicId],
                            balance = walletRow?.get(UserWallets.balance)?.toDouble() ?: 0.0,
                            bonusBalance = walletRow?.get(UserWallets.bonusBalance)?.toDouble() ?: 0.0
                        )
                    } else {
                        null
                    }
                }

                if (userData != null) {
                    call.respond(ApiResponse(code = 0, message = "success", data = userData))
                } else {
                    call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                }
            }
        }
    }
}

@Serializable
data class UserProfileDto(
    val userId: Long,
    val openid: String,
    val phone: String?,
    val nickname: String?,
    val realName: String?,
    val gender: Int?,
    val age: Int?,
    val avatar: String?,
    val primaryClinicId: Long?,
    val balance: Double,
    val bonusBalance: Double
)
