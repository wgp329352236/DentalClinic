package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 积分过期定时任务（需求 3.4.1：积分自获得起 12 个月有效；到期前 30 天提醒）。
 *
 * 每天 09:10 执行两件事：
 * 1. 到期清零：扫 changeAmount>0 且 expired_amount<change_amount 且 expires_at<=now 的发放批次，
 *    按用户聚合后扣减 min(批次剩余总额, 用户当前余额)，写 source_type=points_expired 负流水；
 *    批次一律全额标记 expired_amount=change_amount——余额已被兑换/抵扣提前消耗的部分不扣余额，
 *    视为「已提前消耗」，避免出现负余额。
 * 2. 到期提醒：未来 30 天内到期且未提醒过的批次，按用户聚合发一条提醒（订阅消息降级站内信），
 *    置 expiry_notified_at 保证每批次只提醒一次。
 *
 * 每个用户独立事务：单用户失败不影响其他用户，也不会把整批清零回滚。
 */
object PointsExpireService : BaseService() {

    private val logger = LoggerFactory.getLogger("PointsExpire")

    private const val TPL_POINTS_EXPIRE = "points_expire_remind"
    private const val POINTS_PAGE = "pages/points/points"

    private val dueDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    private data class Batch(val id: Long, val userId: Long, val remaining: Int, val expiresAt: LocalDateTime)

    fun start() {
        val scheduler = newDaemonScheduledExecutor("points-expire-thread")
        scheduler.scheduleDaily("PointsExpire", hour = 9, minutePast = 10) {
            sweepExpired()
            sweepReminders()
        }
        logger.info("[PointsExpire]: 积分过期任务已调度（每日 09:10 清零 + 30 天到期提醒）")
    }

    // ---------------- 到期清零 ----------------

    private suspend fun sweepExpired() {
        val now = LocalDateTime.now()
        val batches = dbQuery {
            PointsRecords.selectAll()
                .where {
                    (PointsRecords.changeAmount greater 0) and
                        (PointsRecords.expiredAmount less PointsRecords.changeAmount) and
                        (PointsRecords.expiresAt lessEq now)
                }
                .toList()
                .map {
                    Batch(
                        id = it[PointsRecords.id],
                        userId = it[PointsRecords.userId],
                        remaining = it[PointsRecords.changeAmount] - it[PointsRecords.expiredAmount],
                        expiresAt = it[PointsRecords.expiresAt]
                    )
                }
        }
        if (batches.isEmpty()) return

        val byUser = batches.groupBy { it.userId }
        var deductedUsers = 0
        var deductedPoints = 0
        byUser.forEach { (userId, userBatches) ->
            try {
                val deducted = expireForUser(userId, userBatches.map { it.id }, userBatches.sumOf { it.remaining })
                if (deducted > 0) {
                    deductedUsers++
                    deductedPoints += deducted
                }
            } catch (e: Exception) {
                logger.warn("[PointsExpire]: 用户 {} 积分清零失败: {}", userId, e.message)
            }
        }
        logger.info(
            "[PointsExpire]: 到期批次 {} 个涉及 {} 人，实际扣减 {} 人共 {} 分",
            batches.size, byUser.size, deductedUsers, deductedPoints
        )
    }

    /** @return 实际扣减的积分数（0 表示批次均已被提前消耗，仅做标记） */
    private suspend fun expireForUser(userId: Long, batchIds: List<Long>, batchRemaining: Int): Int = dbQuery {
        val balance = UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

        val deduct = minOf(balance, batchRemaining).coerceAtLeast(0)

        if (deduct > 0) {
            // 条件更新兜底并发：余额必须仍足够扣减
            val flipped = UserPointsSummary.update({
                (UserPointsSummary.userId eq userId) and
                    (UserPointsSummary.totalBalance greaterEq deduct)
            }) {
                it[UserPointsSummary.totalBalance] = UserPointsSummary.totalBalance - deduct
            }
            if (flipped == 0) {
                logger.warn("[PointsExpire]: 用户 {} 余额并发变化，本次清零跳过（下轮重试）", userId)
                return@dbQuery -1
            }

            PointsRecords.insert {
                it[PointsRecords.userId] = userId
                it[PointsRecords.changeAmount] = -deduct
                it[PointsRecords.balanceAfter] = balance - deduct
                it[PointsRecords.sourceType] = "points_expired"
                it[PointsRecords.sourceId] = null
                it[PointsRecords.description] = "积分到期清零（有效期12个月）"
                // 负流水不参与过期判定，expires_at 为 NOT NULL，记当前时间即可
                it[PointsRecords.expiresAt] = LocalDateTime.now()
            }
        }

        // 批次全额标记：未扣余额的部分视为已被兑换/抵扣提前消耗
        PointsRecords.update({
            (PointsRecords.id inList batchIds) and
                (PointsRecords.expiredAmount less PointsRecords.changeAmount)
        }) {
            it[PointsRecords.expiredAmount] = PointsRecords.changeAmount
        }
        deduct
    }.let { if (it < 0) 0 else it }

    // ---------------- 到期前 30 天提醒 ----------------

    private suspend fun sweepReminders() {
        val now = LocalDateTime.now()
        val deadline = now.plusDays(30)

        data class DueBatch(val id: Long, val userId: Long, val remaining: Int, val expiresAt: LocalDateTime)

        val due = dbQuery {
            PointsRecords.selectAll()
                .where {
                    (PointsRecords.changeAmount greater 0) and
                        (PointsRecords.expiredAmount less PointsRecords.changeAmount) and
                        (PointsRecords.expiresAt greater now) and
                        (PointsRecords.expiresAt lessEq deadline) and
                        PointsRecords.expiryNotifiedAt.isNull()
                }
                .toList()
                .map {
                    DueBatch(
                        id = it[PointsRecords.id],
                        userId = it[PointsRecords.userId],
                        remaining = it[PointsRecords.changeAmount] - it[PointsRecords.expiredAmount],
                        expiresAt = it[PointsRecords.expiresAt]
                    )
                }
        }
        if (due.isEmpty()) return

        val byUser = due.groupBy { it.userId }
        byUser.forEach { (userId, batches) ->
            try {
                val total = batches.sumOf { it.remaining }
                val earliest = batches.minOf { it.expiresAt }
                val dateText = earliest.toLocalDate().format(dueDateFormatter)

                // 先认领（置提醒时间戳），再发送；发送失败也不重复提醒，与预约提醒同一取舍
                dbQuery {
                    PointsRecords.update({
                        (PointsRecords.id inList batches.map { it.id }) and
                            PointsRecords.expiryNotifiedAt.isNull()
                    }) {
                        it[PointsRecords.expiryNotifiedAt] = now
                    }
                }

                ScheduledNotifier.notify(
                    userId = userId,
                    templateKey = TPL_POINTS_EXPIRE,
                    title = "积分即将过期提醒",
                    content = "您有 $total 积分将于 $dateText 到期，到期后将自动清零，" +
                        "可前往「积分商城」兑换口腔检查或精美礼品。",
                    subscribeData = mapOf(
                        "thing1" to "$total 积分即将过期",
                        "time2" to dateText,
                        "thing3" to "可前往积分商城兑换"
                    ),
                    page = POINTS_PAGE,
                    msgType = "activity"
                )
            } catch (e: Exception) {
                logger.warn("[PointsExpire]: 用户 {} 到期提醒失败: {}", userId, e.message)
            }
        }
        logger.info("[PointsExpire]: 30 天内到期提醒已发送 {} 人", byUser.size)
    }
}
