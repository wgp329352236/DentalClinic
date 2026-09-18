package com.dentalclinic.common.config

import com.dentalclinic.admin.entity.AdminSystemConfigs
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

/**
 * 预约规则配置（需求 3.2.4）。
 *
 * 将原先硬编码在 [com.dentalclinic.user.service.AppointmentService] 中的常量
 * （取消提前分钟数、爽约次数阈值、爽约窗口天数、是否允许当天预约）抽到
 * `admin_system_config` 表，管理端「系统设置 → 系统配置」可直接修改，热加载生效。
 *
 * 读取策略：查询失败 / 配置不存在时回落到默认值（与原硬编码一致），保证不会因
 * 配置缺失导致预约链路不可用。读操作可在调用方已有事务内执行（Exposed R2DBC
 * 支持在同一连接上嵌套查询），也可独立调用。
 */
object AppointmentConfig {

    private val logger = LoggerFactory.getLogger("AppointmentConfig")

    /** 自助取消/改约需提前的分钟数（默认 120 = 2 小时） */
    const val KEY_CANCEL_DEADLINE_MINUTES = "appointment.cancel_deadline_minutes"

    /** 爽约次数阈值（默认 3 次） */
    const val KEY_NO_SHOW_LIMIT = "appointment.no_show_limit"

    /** 爽约统计窗口天数（默认 30 天） */
    const val KEY_NO_SHOW_WINDOW_DAYS = "appointment.no_show_window_days"

    /** 是否允许当天预约（1=允许，0=不允许；默认 1） */
    const val KEY_ALLOW_SAME_DAY = "appointment.allow_same_day"

    private val DEFAULTS = mapOf(
        KEY_CANCEL_DEADLINE_MINUTES to 120,
        KEY_NO_SHOW_LIMIT to 3,
        KEY_NO_SHOW_WINDOW_DAYS to 30,
        KEY_ALLOW_SAME_DAY to 1
    )

    /**
     * 读取整型配置；不存在或解析失败时返回默认值。
     *
     * 可在事务内外调用：内部用 suspendTransaction 包裹，若外层已有事务则复用同一连接。
     */
    suspend fun getInt(key: String): Int {
        val default = DEFAULTS[key] ?: 0
        return try {
            suspendTransaction(DBConfig.database) {
                val row = AdminSystemConfigs.selectAll()
                    .where { AdminSystemConfigs.configKey eq key }
                    .singleOrNull()
                    ?: return@suspendTransaction default
                row[AdminSystemConfigs.configValue].toIntOrNull() ?: default
            }
        } catch (e: Exception) {
            logger.warn("[AppointmentConfig] 读取配置 {} 失败，使用默认值 {}: {}", key, default, e.message)
            default
        }
    }

    suspend fun cancelDeadlineMinutes(): Long = getInt(KEY_CANCEL_DEADLINE_MINUTES).toLong()

    suspend fun noShowLimit(): Int = getInt(KEY_NO_SHOW_LIMIT)

    suspend fun noShowWindowDays(): Long = getInt(KEY_NO_SHOW_WINDOW_DAYS).toLong()

    /** 是否允许当天预约 */
    suspend fun allowSameDay(): Boolean = getInt(KEY_ALLOW_SAME_DAY) != 0
}
