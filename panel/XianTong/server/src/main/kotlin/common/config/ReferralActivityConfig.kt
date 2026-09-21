package com.dentalclinic.common.config

import com.dentalclinic.admin.entity.AdminSystemConfigs
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * 推荐活动配置（需求 4.8「支持配置推荐活动的有效期（如限时双倍奖励活动）」）。
 *
 * 与 [AppointmentConfig] 同一套 `admin_system_config` 配置链路：管理端
 * 「系统设置 → 系统配置」可直接修改，热加载生效；配置缺失/格式错误回落关闭状态，
 * 保证推荐奖励链路不受配置缺失影响。
 *
 * 双倍奖励作用范围：推荐奖励中的「积分」部分（首消积分 / 持续奖励积分 / 阶梯奖励积分），
 * 由 [com.dentalclinic.common.ReferralRewardService] 在发放时翻倍并继续受月度上限约束；
 * 赠送余额与新人券保持原值，不参与翻倍。
 */
object ReferralActivityConfig {

    private val logger = LoggerFactory.getLogger("ReferralActivityConfig")

    /** 是否启用双倍奖励活动（1=启用，0=关闭，默认 0） */
    const val KEY_DOUBLE_ENABLED = "referral.double_bonus_enabled"

    /** 活动开始日期（含当天，yyyy-MM-dd） */
    const val KEY_DOUBLE_START = "referral.double_bonus_start"

    /** 活动结束日期（含当天，yyyy-MM-dd） */
    const val KEY_DOUBLE_END = "referral.double_bonus_end"

    private const val DEFAULT_ENABLED = 0

    /** 读取整型配置，不存在/解析失败回落默认值。可在事务内外调用。 */
    suspend fun getInt(key: String, default: Int): Int = try {
        suspendTransaction(DBConfig.database) {
            AdminSystemConfigs.selectAll()
                .where { AdminSystemConfigs.configKey eq key }
                .singleOrNull()
                ?.get(AdminSystemConfigs.configValue)
                ?.toIntOrNull() ?: default
        }
    } catch (e: Exception) {
        logger.warn("[ReferralActivity] 读取配置 {} 失败，使用默认值 {}: {}", key, default, e.message)
        default
    }

    /** 读取日期型配置（yyyy-MM-dd），不存在/解析失败返回 null。 */
    suspend fun getDate(key: String): LocalDate? = try {
        val raw = suspendTransaction(DBConfig.database) {
            AdminSystemConfigs.selectAll()
                .where { AdminSystemConfigs.configKey eq key }
                .singleOrNull()
                ?.get(AdminSystemConfigs.configValue)
                ?.trim()
        }
        raw?.takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                logger.warn("[ReferralActivity] 配置 {} 日期格式非法: {}，忽略", key, it)
                null
            }
        }
    } catch (e: Exception) {
        logger.warn("[ReferralActivity] 读取配置 {} 失败: {}", key, e.message)
        null
    }

    /**
     * 当前是否处于双倍奖励活动期内。
     *
     * 判定：enabled=1 且今天落在 [start, end] 闭区间内。
     * start/end 未配置时不视为活动（缺一不可，避免「半配置」误翻倍）。
     */
    suspend fun doubleBonusActive(): Boolean {
        if (getInt(KEY_DOUBLE_ENABLED, DEFAULT_ENABLED) != 1) return false
        val start = getDate(KEY_DOUBLE_START) ?: return false
        val end = getDate(KEY_DOUBLE_END) ?: return false
        val today = LocalDate.now()
        return !today.isBefore(start) && !today.isAfter(end)
    }
}