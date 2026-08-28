package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.AdminAuditLogs
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Data cleanup service for admin audit logs.
 * Runs daily at 3 AM to delete records older than 90 days.
 */
object AdminCleanupService {

    private val logger = LoggerFactory.getLogger(AdminCleanupService::class.java)

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "admin-cleanup-thread").apply { isDaemon = true }
    }

    fun start() {
        scheduler.scheduleAtFixedRate({ runCleanup() }, 1, 24, TimeUnit.HOURS)
        logger.info("[AdminCleanup]: Audit log cleanup scheduled (daily at 3 AM)")
    }

    private fun runCleanup() {
        try {
            runBlocking {
                suspendTransaction(DBConfig.database) {
                    val cutoff = LocalDateTime.now().minusDays(90)
                    val deleted = AdminAuditLogs.deleteWhere {
                        LessOp(
                            AdminAuditLogs.createdAt,
                            QueryParameter(cutoff, AdminAuditLogs.createdAt.columnType)
                        )
                    }
                    logger.info("[AdminCleanup]: Deleted $deleted audit log(s) older than 90 days")
                }
            }
        } catch (e: Exception) {
            logger.error("[AdminCleanup]: Cleanup failed", e)
        }
    }

    fun shutdown() {
        scheduler.shutdown()
    }
}
