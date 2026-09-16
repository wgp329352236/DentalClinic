package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.UserMemberLevels
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 会员等级年度复评任务（需求 3.3.2：每年 1 月 1 日按上一年度消费重新评定等级）。
 *
 * 规则（与消费链路「只升不降」互补）：
 * - 上一年度折后消费总额 < 当前等级门槛的 50% → 降一级；否则保留；
 * - Lv1 为最低等级，不再下降；
 * - 无论是否降级，都把 last_year_consumption 刷新为上一年实际值，作为本次复评依据存档。
 *
 * 幂等：年度任务无行级标记位，用 system_job_log 中带年份的 jobKey 占位
 * （member_level_review_2026），1 月 1 日当天服务重启重复执行也不会连降多级。
 */
object MemberLevelReviewService : BaseService() {

    private val logger = LoggerFactory.getLogger("MemberLevelReview")

    private val DOWNGRADE_THRESHOLD_RATIO = BigDecimal("0.5")

    fun start() {
        val scheduler = newDaemonScheduledExecutor("member-level-review-thread")
        scheduler.scheduleDaily("MemberLevelReview", hour = 0, minutePast = 35) { runIfDue() }
        logger.info("[MemberLevelReview]: 会员年度复评任务已调度（每年 1 月 1 日 00:35）")
    }

    private suspend fun runIfDue() {
        val today = LocalDate.now()
        if (today.monthValue != 1 || today.dayOfMonth != 1) return

        val reviewYear = today.year - 1 // 被复评的消费年度
        val jobKey = "member_level_review_$reviewYear"
        if (!tryAcquireAnnualJob(jobKey)) {
            logger.info("[MemberLevelReview]: {} 年度复评已执行过，跳过", reviewYear)
            return
        }

        try {
            val downgraded = review(reviewYear)
            markAnnualJobDone(jobKey)
            logger.info("[MemberLevelReview]: {} 年度复评完成，降级 {} 人", reviewYear, downgraded)
        } catch (e: Exception) {
            // 台账占位行已写入但业务失败：为保证次日 tick 可重试，这里需要释放占位。
            // 复评是全量批处理，未做半程提交（整事务回滚），释放后重跑安全。
            logger.error("[MemberLevelReview]: {} 年度复评失败，将在下个 tick 重试", reviewYear, e)
            releaseAnnualJob(jobKey)
        }
    }

    /** @return 实际降级人数 */
    private suspend fun review(reviewYear: Int): Int = suspendTransaction(DBConfig.database) {
        val now = LocalDateTime.now()
        val yearStart = LocalDateTime.of(reviewYear, 1, 1, 0, 0)
        val yearEnd = LocalDateTime.of(reviewYear + 1, 1, 1, 0, 0)

        // 上一年度折后消费（amount 为会员折扣后金额，与升级口径一致），按用户汇总
        val yearlyConsumption = ConsumptionRecords.selectAll()
            .where {
                (ConsumptionRecords.createdAt greaterEq yearStart) and
                    (ConsumptionRecords.createdAt less yearEnd) and
                    (ConsumptionRecords.status eq "success")
            }
            .toList()
            .groupBy { it[ConsumptionRecords.userId] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row[ConsumptionRecords.amount]) }
            }

        // 启用中的等级：id → 等级行；levelCode → 等级行（用于找下一级）
        val levels = MemberLevels.selectAll()
            .where { MemberLevels.status eq 1 }
            .toList()
        val levelById = levels.associateBy { it[MemberLevels.id] }
        val levelByCode = levels.associateBy { it[MemberLevels.levelCode] }

        val userLevels = UserMemberLevels.selectAll().toList()
        var downgradedCount = 0

        userLevels.forEach { row ->
            val userId = row[UserMemberLevels.userId]
            val lastYear = yearlyConsumption[userId] ?: BigDecimal.ZERO
            val currentLevel = levelById[row[UserMemberLevels.levelId]]

            val targetLevel = if (currentLevel != null) {
                val currentCode = currentLevel[MemberLevels.levelCode]
                val threshold = currentLevel[MemberLevels.minConsumption].multiply(DOWNGRADE_THRESHOLD_RATIO)
                if (currentCode > 1 && lastYear.compareTo(threshold) < 0) {
                    levelByCode[currentCode - 1]
                } else null
            } else null

            UserMemberLevels.update({ UserMemberLevels.id eq row[UserMemberLevels.id] }) {
                it[UserMemberLevels.lastYearConsumption] = lastYear
                if (targetLevel != null) {
                    it[UserMemberLevels.levelId] = targetLevel[MemberLevels.id]
                    it[UserMemberLevels.effectiveFrom] = now
                    it[UserMemberLevels.effectiveTo] = null
                }
                it[UserMemberLevels.updatedAt] = now
            }

            if (targetLevel != null) {
                downgradedCount++
                sendNotification(
                    userId = userId,
                    title = "会员等级年度复评通知",
                    content = "根据您 ${reviewYear} 年度的消费记录，您的会员等级已调整为" +
                        "「${targetLevel[MemberLevels.levelName]}」。新年度继续消费可重新升级，感谢您的支持。",
                    msgType = "system"
                )
            }
        }
        downgradedCount
    }
}
