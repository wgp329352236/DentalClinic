package com.dentalclinic.admin.auth

import com.dentalclinic.config.GlobalConfig.appConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.slf4j.LoggerFactory

/**
 * Admin 模块 Redis 客户端（Lettuce）
 * 用于 Token 管理、账户锁定、队列大小缓存等
 */
object AdminRedisClient {

    private val logger = LoggerFactory.getLogger(AdminRedisClient::class.java)

    private val redisUrl: String by lazy {
        val host = appConfig.propertyOrNull("redis.host")?.getString() ?: "localhost"
        val port = appConfig.propertyOrNull("redis.port")?.getString() ?: "6379"
        val db = appConfig.propertyOrNull("redis.database")?.getString() ?: "0"
        val password = appConfig.propertyOrNull("redis.password")?.getString()
        if (password.isNullOrBlank()) {
            "redis://$host:$port/$db"
        } else {
            "redis://$password@$host:$port/$db"
        }
    }

    private val client: RedisClient by lazy {
        val redisUri = io.lettuce.core.RedisURI.create(redisUrl)
        val client = RedisClient.create(redisUri)
        client.options = io.lettuce.core.ClientOptions.builder()
            .protocolVersion(io.lettuce.core.protocol.ProtocolVersion.RESP2)
            .build()
        logger.info("[AdminRedis]: 连接 Redis: ${redisUrl.replace(Regex("//.*@"), "//***@")}")
        client
    }

    private val connection: StatefulRedisConnection<String, String> by lazy {
        client.connect()
    }

    val commands: RedisCommands<String, String> by lazy {
        connection.sync()
    }

    // ============================================================
    // Refresh Token 管理
    // ============================================================

    fun storeRefreshToken(tokenId: String, userId: Long, ttlSeconds: Long) {
        commands.setex("admin:refresh:$tokenId", ttlSeconds, userId.toString())
    }

    fun getRefreshTokenUserId(tokenId: String): Long? {
        return commands.get("admin:refresh:$tokenId")?.toLongOrNull()
    }

    fun deleteRefreshToken(tokenId: String) {
        commands.del("admin:refresh:$tokenId")
    }

    fun deleteAllRefreshTokensForUser(userId: Long) {
        // 扫描所有 admin:refresh:* 并删除属于该用户的
        var cursor: io.lettuce.core.ScanCursor = io.lettuce.core.ScanCursor.INITIAL
        do {
            val result = commands.scan(cursor, io.lettuce.core.ScanArgs.Builder.matches("admin:refresh:*").limit(100))
            cursor = result
            result.keys.forEach { key ->
                if (commands.get(key) == userId.toString()) {
                    commands.del(key)
                }
            }
        } while (!result.isFinished)
    }

    // ============================================================
    // 登录失败计数 & 账户锁定
    // ============================================================

    private const val LOCK_TTL_SECONDS = 30 * 60L // 30 分钟
    private const val FAIL_TTL_SECONDS = 30 * 60L // 30 分钟
    private const val MAX_FAILURES = 5

    fun incrementFailCount(username: String): Long {
        val key = "admin:fail:$username"
        val count = commands.incr(key)
        if (count == 1L) {
            commands.expire(key, FAIL_TTL_SECONDS)
        }
        if (count >= MAX_FAILURES) {
            commands.setex("admin:lock:$username", LOCK_TTL_SECONDS, "1")
        }
        return count
    }

    fun isAccountLocked(username: String): Boolean {
        return commands.get("admin:lock:$username") != null
    }

    fun clearFailCount(username: String) {
        commands.del("admin:fail:$username")
    }

    // ============================================================
    // 批量任务进度
    // ============================================================

    fun setBatchProgress(taskId: String, total: Int, completed: Int, failed: Int, startTime: Long) {
        val key = "admin:batch:$taskId"
        commands.hset(
            key, mapOf(
                "total" to total.toString(),
                "completed" to completed.toString(),
                "failed" to failed.toString(),
                "startTime" to startTime.toString()
            )
        )
        commands.expire(key, 3600) // 1 小时过期
    }

    fun getBatchProgress(taskId: String): Map<String, String>? {
        val key = "admin:batch:$taskId"
        val result = commands.hgetall(key)
        return if (result.isNullOrEmpty()) null else result
    }

    // ============================================================
    // 通用缓存
    // ============================================================

    fun setWithTtl(key: String, value: String, ttlSeconds: Long) {
        commands.setex(key, ttlSeconds, value)
    }

    fun get(key: String): String? = commands.get(key)

    fun del(key: String) {
        commands.del(key)
    }

    /**
     * 关闭连接
     */
    fun shutdown() {
        try {
            connection.close()
            client.shutdown()
            logger.info("[AdminRedis]: 连接已关闭")
        } catch (e: Exception) {
            logger.error("[AdminRedis]: 关闭连接失败", e)
        }
    }
}
