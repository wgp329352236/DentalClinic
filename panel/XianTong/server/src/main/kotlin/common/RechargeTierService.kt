package com.dentalclinic.common

import com.dentalclinic.admin.entity.RechargeTiers
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 充值赠送档位共享服务：C 端下单算赠送、C 端档位展示与后台 CRUD 共用同一份数据源。
 *
 * 数据源为 recharge_tier 表（后台「充值档位」页面维护）。
 * 当表中没有任何启用档位（尚未配置/运营误把档位全部停用）或查询异常时，
 * 回退到 [FALLBACK_TIERS] 内置规则，保证充值主流程不被配置问题中断——
 * 充值是资金链路，宁可沿用旧规则也不能因运营配置缺失而拒单或赠送归零。
 */
class RechargeTierService : BaseService() {

    private val logger = LoggerFactory.getLogger("RechargeTierService")

    /** 档位视图（仅计算所需字段，避免上层直接依赖表定义）。 */
    data class Tier(
        val id: Long,
        val tierName: String,
        val minAmount: BigDecimal,
        val bonusRate: BigDecimal,
        val sortOrder: Int
    )

    /** 启用中的档位，按 sortOrder 升序。 */
    suspend fun listEnabledTiers(): List<Tier> = dbQuery {
        RechargeTiers.selectAll()
            .where { RechargeTiers.status eq 1 }
            .orderBy(RechargeTiers.sortOrder, SortOrder.ASC)
            .toList()
            .map {
                Tier(
                    id = it[RechargeTiers.id],
                    tierName = it[RechargeTiers.tierName],
                    minAmount = it[RechargeTiers.minAmount],
                    bonusRate = it[RechargeTiers.bonusRate],
                    sortOrder = it[RechargeTiers.sortOrder]
                )
            }
    }

    /**
     * 计算充值赠送金额：取启用档位中「起充门槛 ≤ 充值金额」的最高一档，
     * bonus = amount × bonusRate，两位小数向下舍入（赠送金额宁少勿多，杜绝超发）。
     *
     * 无匹配档位（低于最低门槛）返回 0。
     */
    suspend fun calculateBonus(amount: BigDecimal): BigDecimal {
        val tiers = try {
            listEnabledTiers()
        } catch (e: Exception) {
            // 读档失败不应阻断充值：记录告警后按内置规则计算，待 DB 恢复后自动回到表配置
            logger.warn("[RechargeTier]: 读取充值档位失败，回退内置规则: ${e.message}")
            emptyList()
        }
        val effective = tiers.ifEmpty { FALLBACK_TIERS }
        val matched = effective
            .filter { it.minAmount <= amount }
            .maxByOrNull { it.minAmount }
            ?: return BigDecimal.ZERO
        return amount.multiply(matched.bonusRate).setScale(2, RoundingMode.DOWN)
    }

    companion object {
        /**
         * 内置兜底档位（与配置化前的硬编码规则一致）：
         * 1000→10%、3000→15%、5000→20%、10000→25%。
         */
        val FALLBACK_TIERS: List<Tier> = listOf(
            Tier(0, "充1000送10%", BigDecimal("1000"), BigDecimal("0.1000"), 10),
            Tier(0, "充3000送15%", BigDecimal("3000"), BigDecimal("0.1500"), 20),
            Tier(0, "充5000送20%", BigDecimal("5000"), BigDecimal("0.2000"), 30),
            Tier(0, "充10000送25%", BigDecimal("10000"), BigDecimal("0.2500"), 40)
        )

        /** 将 decimal(5,4) 的比例渲染为展示文案：0.1000→"10%"、0.1250→"12.5%"。 */
        fun rateLabel(rate: BigDecimal): String {
            val percent = rate.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()
            return "${percent}%"
        }

        /** 按门槛金额与比例计算「充 X 送 Y」展示用赠送额（两位小数向下舍入）。 */
        fun bonusOf(minAmount: BigDecimal, rate: BigDecimal): BigDecimal =
            minAmount.multiply(rate).setScale(2, RoundingMode.DOWN)
    }
}
