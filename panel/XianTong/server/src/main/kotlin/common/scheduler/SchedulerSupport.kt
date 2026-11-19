package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.SystemJobLogs
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 定时任务公共设施，与 AdminCleanupService 同一范式：
 * 各任务一个 object、一个 daemon 单线程调度器、任务体 runBlocking + 独立事务，
 * 任何异常只记录日志，绝不让调度线程死掉。
 */

private val sharedLogger = LoggerFactory.getLogger("SchedulerSupport")

/** 构造命名的 daemon 单线程调度器。 */
internal fun newDaemonScheduledExecutor(name: String) =
    Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

/** 距下一个整点（minutePast 分）的毫秒数；该时刻已过则顺延一小时。 */
internal fun millisUntilNextHour(minutePast: Int = 5): Long {
    val now = LocalDateTime.now()
    var target = now.toLocalDate().atTime(now.hour, minutePast)
    if (!target.isAfter(now)) target = target.plusHours(1)
    return Duration.between(now, target).toMillis()
}

/** 距下一个指定时刻（每天 hour:minutePast）的毫秒数；已过则顺延一天。 */
internal fun millisUntilDaily(hour: Int, minutePast: Int = 5): Long {
    val now = LocalDateTime.now()
    var target = now.toLocalDate().atTime(hour, minutePast)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return Duration.between(now, target).toMillis()
}

/** 调度任务体的统一包装：runBlocking，异常只 log。 */
internal fun runScheduled(name: String, block: suspend () -> Unit): () -> Unit = {
    try {
        runBlocking { block() }
    } catch (e: Exception) {
        sharedLogger.error("[$name]: 定时任务执行失败", e)
    }
}

/**
 * 判断年度任务在给定年份是否已执行过；未执行时立即占位（先占后做），
 * 防止服务在 1 月 1 日当天重启导致会员复评连降多级。
 *
 * @return true 表示本次抢到执行权（调用方应执行业务，完成后调 [markAnnualJobDone]）；
 *         false 表示今年已执行过，应跳过。
 */
internal suspend fun tryAcquireAnnualJob(jobKey: String): Boolean =
    suspendTransaction(DBConfig.database) {
        val exists = SystemJobLogs.selectAll()
            .where { SystemJobLogs.jobKey eq jobKey }
            .toList()
            .isNotEmpty()
        if (exists) {
            false
        } else {
            SystemJobLogs.insert { it[SystemJobLogs.jobKey] = jobKey }
            true
        }
    }

/** 年度任务完成后刷新执行时间（占位行的时间戳为创建时间，此处更新为完成时间）。 */
internal suspend fun markAnnualJobDone(jobKey: String) {
    suspendTransaction(DBConfig.database) {
        SystemJobLogs.update({ SystemJobLogs.jobKey eq jobKey }) {
            it[SystemJobLogs.lastRunAt] = LocalDateTime.now()
        }
    }
}

/**
 * 年度任务业务失败时释放占位，使下一个 tick 可以重试。
 * 业务本身为单一大事务（失败全回滚），释放占位不会造成半程状态。
 */
internal suspend fun releaseAnnualJob(jobKey: String) {
    suspendTransaction(DBConfig.database) {
        SystemJobLogs.deleteWhere { SystemJobLogs.jobKey eq jobKey }
    }
}

/** 便捷注册：每小时固定分钟执行一次（启动时对齐到下一个整点）。 */
internal fun java.util.concurrent.ScheduledExecutorService.scheduleHourly(
    name: String,
    minutePast: Int = 5,
    block: suspend () -> Unit
) {
    val task = runScheduled(name, block)
    scheduleAtFixedRate(task, millisUntilNextHour(minutePast), 60 * 60 * 1000L, TimeUnit.MILLISECONDS)
}

/** 便捷注册：每天固定时刻执行一次。 */
internal fun java.util.concurrent.ScheduledExecutorService.scheduleDaily(
    name: String,
    hour: Int,
    minutePast: Int = 5,
    block: suspend () -> Unit
) {
    val task = runScheduled(name, block)
    scheduleAtFixedRate(task, millisUntilDaily(hour, minutePast), 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS)
}
