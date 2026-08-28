package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.config.DBConfig
import com.dentalclinic.user.auth.UserJwtConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.LocalDateTime

/**
 * User authentication service for C-end patients.
 */
class UserAuthService {

    /**
     * Login/register via WeChat code (simulated for testing).
     */
    suspend fun wechatLogin(code: String): WechatLoginResponse = withContext(Dispatchers.IO) {
        // Simulate WeChat code-to-openid exchange
        val openid = "mock_openid_${code}"
        val unionid: String? = null

        suspendTransaction(DBConfig.database) {
            // Check if user exists
            val existingRow = Users.selectAll()
                .where { Users.openid eq openid }
                .toList()
                .singleOrNull()

            val userId: Long
            val isNewUser: Boolean

            if (existingRow != null) {
                // Existing user - update last login
                userId = existingRow[Users.id]
                Users.update({ Users.id eq userId }) {
                    it[Users.lastLoginAt] = LocalDateTime.now()
                }
                isNewUser = false
            } else {
                // New user - create with default values
                val insertResult = Users.insert {
                    it[Users.openid] = openid
                    it[Users.unionid] = unionid
                    it[Users.sourceType] = "self"
                    it[Users.status] = 1
                }
                userId = insertResult[Users.id]

                // Initialize wallet
                UserWallets.insert {
                    it[UserWallets.userId] = userId
                    it[UserWallets.balance] = 0.0.toBigDecimal()
                    it[UserWallets.bonusBalance] = 0.0.toBigDecimal()
                    it[UserWallets.version] = 0
                }

                isNewUser = true
            }

            // Generate tokens
            val accessToken = UserJwtConfig.generateAccessToken(userId, openid)
            val refreshToken = UserJwtConfig.generateRefreshToken(userId)

            WechatLoginResponse(
                userId = userId,
                openid = openid,
                accessToken = accessToken,
                refreshToken = refreshToken,
                isNewUser = isNewUser
            )
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    suspend fun refreshToken(refreshToken: String): String? {
        val decoded = UserJwtConfig.verifyToken(refreshToken.replace("Bearer ", ""))
            ?: return null

        // Verify it's actually a refresh token
        if (decoded.getClaim("type").asString() != "refresh") return null

        val userId = decoded.subject.toLong()
        // Get openid from user record since refresh token doesn't contain it
        val openid = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                Users.selectAll()
                    .where { Users.id eq userId }
                    .toList()
                    .singleOrNull()
                    ?.get(Users.openid)
            }
        } ?: return null

        return UserJwtConfig.generateAccessToken(userId, openid)
    }
}

data class WechatLoginResponse(
    val userId: Long,
    val openid: String,
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean
)
