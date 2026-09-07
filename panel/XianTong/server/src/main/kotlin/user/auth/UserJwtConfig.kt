package com.dentalclinic.user.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

/**
 * User-end JWT configuration for C-end patients.
 */
object UserJwtConfig {
    private const val SECRET = "user-jwt-secret-change-me-in-production"
    private const val ISSUER = "dental-clinic-user"
    private const val ACCESS_TOKEN_EXPIRY_HOURS = 24L
    private const val REFRESH_TOKEN_EXPIRY_DAYS = 30L

    private val algorithm = Algorithm.HMAC256(SECRET)

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
