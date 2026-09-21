package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.wx.WechatApiException
import com.dentalclinic.common.wx.WechatConfig
import com.dentalclinic.common.wx.WechatMiniClient
import com.dentalclinic.user.auth.UserJwtConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * User authentication service for C-end patients.
 */
class UserAuthService : BaseService() {

    private val logger = LoggerFactory.getLogger("UserAuthService")

    /**
     * 微信资料（小程序 wx.getUserProfile 兜底返回，字段都允许为空）。
     */
    data class WxProfile(
        val nickname: String? = null,
        val avatarUrl: String? = null,
        val gender: Int? = null
    )

    /**
     * Login/register via WeChat code.
     *
     * 已配置 appId/appSecret 时走真实 auth.code2Session；未配置时降级为 mock_openid，
     * 保证本地/演示环境在没有微信凭证时仍可联调（mock 登录的用户与真实 openid 天然隔离）。
     *
     * @param phoneCode getPhoneNumber 回调的动态令牌；为空表示本次未授权手机号
     */
    suspend fun wechatLogin(
        code: String,
        phoneCode: String? = null,
        profile: WxProfile? = null
    ): WechatLoginResponse {
        // 外部 HTTP 调用必须放在数据库事务之外：事务里挂起等待微信接口会长时间占用连接
        val session = resolveSession(code)
        val phone = resolvePhone(phoneCode)

        return dbQuery {
            // Check if user exists
            val existingRow = Users.selectAll()
                .where { Users.openid eq session.openid }
                .toList()
                .singleOrNull()

            val userId: Long
            val isNewUser: Boolean

            if (existingRow != null) {
                // 已注销账户：禁止复用原 openid 再次登录（需求 5.2 用户数据删除）
                if (existingRow[Users.status] == 0) {
                    throw AccountDeletedException()
                }
                // Existing user - update last login, and backfill previously missing info
                userId = existingRow[Users.id]
                Users.update({ Users.id eq userId }) {
                    it[Users.lastLoginAt] = LocalDateTime.now()
                    // 用户先静默登录、后授权手机号/资料的场景：仅在原值为空时补，不覆盖已改资料
                    if (phone != null && existingRow[Users.phone] == null) it[Users.phone] = phone
                    val nickname = profile?.nickname?.takeIf { v -> v.isNotBlank() }
                    if (nickname != null && existingRow[Users.nickname] == null) it[Users.nickname] = nickname
                    val avatar = profile?.avatarUrl?.takeIf { v -> v.isNotBlank() }
                    if (avatar != null && existingRow[Users.avatar] == null) it[Users.avatar] = avatar
                }
                isNewUser = false
            } else {
                // New user - create with default values
                val insertResult = Users.insert {
                    it[Users.openid] = session.openid
                    it[Users.unionid] = session.unionid
                    // 左值必须限定 Users.phone：插入块以表为接收者，裸写 phone 会被
                    // 解析成本地 String? 变量（局部变量优先于接收者成员），导致类型错误
                    it[Users.phone] = phone
                    it[Users.nickname] = profile?.nickname?.takeIf { v -> v.isNotBlank() }
                    it[Users.avatar] = profile?.avatarUrl?.takeIf { v -> v.isNotBlank() }
                    it[Users.gender] = profile?.gender?.takeIf { g -> g == 1 || g == 2 }
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
            val accessToken = UserJwtConfig.generateAccessToken(userId, session.openid)
            val refreshToken = UserJwtConfig.generateRefreshToken(userId)

            WechatLoginResponse(
                userId = userId,
                openid = session.openid,
                accessToken = accessToken,
                refreshToken = refreshToken,
                isNewUser = isNewUser
            )
        }
    }

    /**
     * code2session：配置齐全走真实接口，否则降级 mock（仅限本地开发）。
     */
    private suspend fun resolveSession(code: String): SessionInfo {
        if (!WechatConfig.miniApp.enabled) {
            return SessionInfo(openid = "mock_openid_$code", unionid = null)
        }
        val session = try {
            WechatMiniClient.jscode2session(code)
        } catch (e: WechatApiException) {
            // 40029 invalid code / 45011 frequency limit 等都属于用户可重试错误，
            // 日志保留细节，交给路由层返回通用提示
            logger.warn("[Auth]: code2session 失败: ${e.message}")
            throw e
        }
        return SessionInfo(openid = session.openid, unionid = session.unionid)
    }

    /**
     * 手机号换取。失败不阻断登录主流程：登录态以 openid 为准，
     * 手机号可以稍后在「编辑资料」里再补。
     */
    private suspend fun resolvePhone(phoneCode: String?): String? {
        if (phoneCode.isNullOrBlank()) return null
        if (!WechatConfig.miniApp.enabled) return null
        return try {
            WechatMiniClient.getPhoneNumber(phoneCode)
        } catch (e: Exception) {
            logger.warn("[Auth]: 获取微信手机号失败，不阻断登录: ${e.message}")
            null
        }
    }

    private data class SessionInfo(val openid: String, val unionid: String?)

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
        val openid = dbQuery {
            Users.selectAll()
                .where { Users.id eq userId }
                .toList()
                .singleOrNull()
                ?.get(Users.openid)
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

/** 账户已注销（status=0）后再次尝试登录时抛出，路由层映射为 403「账号已注销」。 */
class AccountDeletedException : RuntimeException("账号已注销，无法登录")
