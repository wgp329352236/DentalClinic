package com.dentalclinic.common.config

import com.dentalclinic.admin.entity.AdminSystemConfigs
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

/**
 * 积分抵扣诊疗费规则配置（需求 3.4.2）。
 *
 * 与 [AppointmentConfig]、[ReferralActivityConfig] 同一套 `admin_system_config` 配置链路：
 * 管理端「系统设置 → 系统配置」可直接修改，热加载生效；配置缺失/非法回落默认值，
 * 保证消费结算链路不会因配置缺失而中断。
 *
 * 抵扣口径：每 [KEY_POINTS_PER_YUAN] 积分抵 1 元；单笔积分最多抵扣「折后应付减券抵扣后金额」
 * 的 [KEY_MAX_RATIO_PERCENT]%（对应需求「单笔最高抵扣消费金额的 20%」）。
 * 总开关 [KEY_ENABLED] 关闭时结算链路完全跳过积分抵扣。
 */
object PointsDeductConfig {

    private val logger = LoggerFactory.getLogger("PointsDeductConfig")

    /** 是否启用积分抵扣诊疗费（1=启用，0=关闭；默认启用） */
    const val KEY_ENABLED = "points.deduct_enabled"

    /** 抵扣比率：多少积分抵 1 元（默认 100 = 100 积分 = 1 元） */
    const val KEY_POINTS_PER_YUAN = "points.deduct_points_per_yuan"

    /** 单笔最高抵扣比例（%）：积分最多抵掉本次应付金额的百分之几（默认 20） */
    const val KEY_MAX_RATIO_PERCENT = "points.deduct_max_ratio_percent"

    private val DEFAULTS = mapOf(
        KEY_ENABLED to 1,
        KEY_POINTS_PER_YUAN to 100,
        KEY_MAX_RATIO_PERCENT to 20
    )

    /** 读取整型配置；不存在或解析失败回落默认值。可在事务内外调用（复用外层连接）。 */
    suspend fun getInt(key: String): Int {
        val default = DEFAULTS[key] ?: 0
        return try {
            suspendTransaction(DBConfig.database) {
                AdminSystemConfigs.selectAll()
                    .where { AdminSystemConfigs.configKey eq key }
                    .singleOrNull()
                    ?.get(AdminSystemConfigs.configValue)
                    ?.toIntOrNull() ?: default
            }
        } catch (e: Exception) {
            logger.warn("[PointsDeductConfig] 读取配置 {} 失败，回落默认值 {}: {}", key, default, e.message)
            default
        }
    }

    suspend fun enabled(): Boolean = getInt(KEY_ENABLED) != 0

    /** 多少积分抵 1 元；非法（≤0）时回落 100，避免除零。 */
    suspend fun pointsPerYuan(): Int = getInt(KEY_POINTS_PER_YUAN).takeIf { it > 0 } ?: 100

    /** 单笔最高抵扣比例（%）；限制在 0..100，非法回落 20。 */
    suspend fun maxRatioPercent(): Int = getInt(KEY_MAX_RATIO_PERCENT).coerceIn(0, 100)
}
