package com.dentalclinic.user.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.dentalclinic.common.config.GlobalConfig
import java.util.*

/**
 * User-end JWT configuration for C-end patients.
 *
 * 签名密钥解析顺序：环境变量 USER_JWT_SECRET > yaml user.jwt.secret；
 * 生产环境（APP_ENV=prod）两者都缺失时直接 fail-fast，非生产环境回退开发默认值并打 WARN。
 */
object UserJwtConfig {
    private const val DEV_DEFAULT_SECRET = "user-jwt-secret-change-me-in-production"
    private const val ISSUER = "dental-clinic-user"
    private const val ACCESS_TOKEN_EXPIRY_HOURS = 24L
    private const val REFRESH_TOKEN_EXPIRY_DAYS = 30L

    private val secret: String by lazy {
        GlobalConfig.requireSecret(
            envName = "USER_JWT_SECRET",
            yamlPath = "user.jwt.secret",
            devDefault = DEV_DEFAULT_SECRET,
            label = "用户端 JWT 签名密钥"
        )
    }

    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(secret) }

    /**
     * Generate access token for a user.
     */
    fun generateAccessToken(userId: Long, openid: String): String {
        val now = Date()
        val expiresAt = Date(now.time + ACCESS_TOKEN_EXPIRY_HOURS * 3600 * 1000)
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withClaim("openid", openid)
            .withClaim("type", "access")
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    /**
     * Generate refresh token for a user.
     */
    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiresAt = Date(now.time + REFRESH_TOKEN_EXPIRY_DAYS * 86400 * 1000)
        val tokenId = UUID.randomUUID().toString()
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withClaim("type", "refresh")
            .withClaim("tokenId", tokenId)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    /**
     * Verify and decode an access token.
     */
    fun verifyAccessToken(token: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
            val decoded = verifier.verify(token)
            if (decoded.getClaim("type").asString() == "access") decoded else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verify any token (access or refresh).
     */
    fun verifyToken(token: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
            verifier.verify(token)
        } catch (e: Exception) {
            null
        }
    }
}
