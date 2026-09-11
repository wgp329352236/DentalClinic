package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.common.config.DBConfig
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserProfileRoutes")

/**
 * User profile routes (protected by JWT).
 */
fun Application.userProfileRoutes() {
    routing {
        route("/api/v1/profile") {
            userAuthenticated {
            /**
             * Get current user's profile.
             * GET /api/v1/profile/me
             * Header: Authorization: Bearer <access_token>
             */
            get("/me") {
                val userId = call.userId()

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

            /**
             * Update user's profile information.
             * PUT /api/v1/profile/me
             * Body: { "realName": "张三", "phone": "13800138000", "gender": 1, "age": 25, "avatar": "url" }
             */
            put("/me") {
                val userId = call.userId()

                try {
                    val request = call.receive<UpdateProfileRequest>()

                    // Validate phone format if provided
                    if (!request.phone.isNullOrEmpty()) {
                        if (!request.phone.matches(Regex("^1[3-9]\\d{9}$"))) {
                            call.respond(ApiResponse<Unit>(code = 400, message = "手机号格式不正确"))
                            return@put
                        }
                    }

                    // Validate gender (1=male, 2=female)
                    if (request.gender != null && request.gender !in listOf(1, 2)) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "性别值无效，应为1(男)或2(女)"))
                        return@put
                    }

                    // Validate age
                    if (request.age != null && (request.age !in 0..150)) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "年龄值无效"))
                        return@put
                    }

                    val updated = suspendTransaction(DBConfig.database) {
                        Users.update({ Users.id eq userId }) {
                            if (request.realName != null) it[Users.realName] = request.realName
                            if (request.phone != null) it[Users.phone] = request.phone
                            if (request.gender != null) it[Users.gender] = request.gender
                            if (request.age != null) it[Users.age] = request.age
                            if (request.avatar != null) it[Users.avatar] = request.avatar
                            if (request.nickname != null) it[Users.nickname] = request.nickname
                        }
                    }

                    if (updated > 0) {
                        call.respond(ApiResponse<Unit>(code = 0, message = "更新成功"))
                    } else {
                        call.respond(ApiResponse<Unit>(code = 404, message = "用户不存在"))
                    }
                } catch (e: Exception) {
                    // scene 里只记 userId，不记 request：手机号/真实姓名属个人敏感信息，不应落入日志
                    val (code, message) = translateApiError(
                        logger, "[Profile]: 用户 $userId 更新资料", "更新失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }
            }
        }
    }
}


@Serializable
data class UpdateProfileRequest(
    val realName: String? = null,
    val phone: String? = null,
    val gender: Int? = null,
    val age: Int? = null,
    val avatar: String? = null,
    val nickname: String? = null
)

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
