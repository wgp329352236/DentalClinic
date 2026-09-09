package com.dentalclinic.admin.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.dentalclinic.common.config.GlobalConfig.appConfig
import java.util.*

/**
 * JWT 配置：Access Token (2h) + Refresh Token (7d)
 */
object JwtConfig {

    private val secret: String by lazy {
        appConfig.propertyOrNull("admin.jwt.secret")?.getString() ?: "admin-default-secret-change-me"
    }

    private val issuer: String by lazy {
        appConfig.propertyOrNull("admin.jwt.issuer")?.getString() ?: "ktor-bi-admin"
    }

    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(secret) }

    /** Access Token 有效期：2 小时 */
    private const val ACCESS_TOKEN_VALIDITY_MS = 2 * 60 * 60 * 1000L

    /** Refresh Token 有效期：7 天 */
    private const val REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * 生成 Access Token
     */
    fun generateAccessToken(userId: Long, username: String, permissions: List<String>): String {
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withClaim("username", username)
            .withClaim("permissions", permissions)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY_MS))
            .withIssuedAt(Date())
            .sign(algorithm)
    }

    /**
     * 生成 Refresh Token
     */
    fun generateRefreshToken(userId: Long): Pair<String, String> {
        val tokenId = UUID.randomUUID().toString()
        val token = JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withClaim("type", "refresh")
            .withClaim("tokenId", tokenId)
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS))
            .withIssuedAt(Date())
            .sign(algorithm)
        return tokenId to token
    }

    /**
     * 验证并解码 Token
     */
    fun verifyToken(token: String): DecodedJWT? {
        return try {
            JWT.require(algorithm)
                .withIssuer(issuer)
                .build()
                .verify(token)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 Access Token 中提取 userId
     */
    fun getUserId(jwt: DecodedJWT): Long = jwt.subject.toLong()

    /**
     * 从 Access Token 中提取 username
     */
    fun getUsername(jwt: DecodedJWT): String = jwt.getClaim("username").asString()

    /**
     * 从 Access Token 中提取权限列表
     */
    fun getPermissions(jwt: DecodedJWT): List<String> =
        jwt.getClaim("permissions").asList(String::class.java) ?: emptyList()

    /**
     * 从 Refresh Token 中提取 tokenId
     */
    fun getTokenId(jwt: DecodedJWT): String = jwt.getClaim("tokenId").asString()

    /**
     * 获取 Token 类型
     */
    fun getTokenType(jwt: DecodedJWT): String = jwt.getClaim("type").asString()

    /** Refresh Token TTL (秒) 用于 Redis */
    const val REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60L
}
