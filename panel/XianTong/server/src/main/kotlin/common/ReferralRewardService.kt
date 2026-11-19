package com.dentalclinic.common

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.ReferralRelations
import com.dentalclinic.admin.entity.ReferralRewards
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.common.config.ReferralActivityConfig
import com.dentalclinic.common.scheduler.ScheduledNotifier
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 推荐奖励发放服务（需求 3.5.2）。
 *
 * 放在 `common` 包的原因：user 端（注册链路）与 admin 端（消费录入链路）都需要发放推荐奖励，
 * 落在任一侧都会引入跨模块反向依赖。
 *
 * 事务约定：本类所有方法都是「事务内方法」——plain suspend，不自行开启事务，
 * 与 [BaseService.addPointsToUser] 保持一致。调用方必须已处于 `dbQuery { }` 中，
 * 这样「消费入库 + 奖励发放 + 站内消息」才能原子提交或整体回滚。
 */
class ReferralRewardService : BaseService() {

    private val logger = LoggerFactory.getLogger("ReferralRewardService")

    /**
     * 一笔消费结算完成后，发放推荐人应得的奖励。
     *
     * 分支规则：
     * - 推荐关系尚未记录首消时间 → 本笔即首消，发「首消奖励」（推荐人积分+赠送余额，被推荐人新人券），
     *   并顺带检查「阶梯奖励」。
     * - 已有首消时间 → 属「后续消费」，按每满 1000 元发 10 积分的「持续奖励」。
     *
     * @param refereeId 被推荐人（本次消费的顾客）
     * @param payAmount 本笔实付金额，持续奖励的计算基数
     * @param consumptionRecordId 消费记录ID，写入积分流水与奖励记录用于溯源
     */
    suspend fun settleConsumptionReward(
        refereeId: Long,
        payAmount: BigDecimal,
        consumptionRecordId: Long? = null
    ): ReferralRewardOutcome {
        // getReferralCode() 会插入 referrerId == refereeId 的占位记录来保存推荐码，
        // 因此所有关系查询都必须排除自推荐。
        val relation = ReferralRelations.selectAll()
            .where { ReferralRelations.refereeId eq refereeId }
            .toList()
            .firstOrNull { row -> row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId] }
            ?: return ReferralRewardOutcome.NoRelation

        val referrerId = relation[ReferralRelations.referrerId]

        return if (relation[ReferralRelations.firstConsumedAt] == null) {
            grantFirstConsumptionReward(relation, referrerId, refereeId, consumptionRecordId)
        } else {
            grantOngoingReward(referrerId, refereeId, payAmount, consumptionRecordId)
        }
    }

    /**
     * 首消奖励：推荐人得注册奖励(20积分) + 首消奖励(100积分+50元)，被推荐人得注册奖励(50积分) + 新人券。
     * P1-8: 注册奖励延迟到首消后发放。
     */
    private suspend fun grantFirstConsumptionReward(
        relation: ResultRow,
        referrerId: Long,
        refereeId: Long,
        consumptionRecordId: Long?
    ): ReferralRewardOutcome {
        val now = LocalDateTime.now()
        val relationId = relation[ReferralRelations.id]

        // P1-8: 推荐人注册奖励 (20积分)
        val registrationPoints = awardCappedPoints(
            userId = referrerId,
            desired = REGISTRATION_REFERRER_POINTS,
            sourceType = SOURCE_REGISTRATION,
            description = "推荐注册奖励-推荐人",
            sourceId = consumptionRecordId
        )

        // P1-8: 被推荐人注册奖励 (50积分)
        val refereeRegistrationPoints = awardCappedPoints(
            userId = refereeId,
            desired = REFEREE_REGISTRATION_POINTS,
            sourceType = SOURCE_REGISTRATION,
            description = "推荐注册奖励-被推荐人",
            sourceId = consumptionRecordId
        )

        // 首消奖励：推荐人得积分 + 赠送余额
        val points = awardCappedPoints(
            userId = referrerId,
            desired = firstConsumptionPoints(),
            sourceType = SOURCE_FIRST_CONSUMPTION,
            description = "推荐首消奖励-积分",
            sourceId = consumptionRecordId
        )

        // 余额奖励计入赠送余额（bonusBalance），不污染充值本金余额
        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq referrerId }
            .toList()
            .singleOrNull()
        val bonus = firstConsumptionBonusYuan()
        if (wallet != null) {
            val bonusBefore = wallet[UserWallets.bonusBalance]
            val bonusAfter = bonusBefore.add(bonus)
            UserWallets.update({ UserWallets.userId eq referrerId }) {
                it[UserWallets.bonusBalance] = bonusAfter
                it[UserWallets.version] = wallet[UserWallets.version] + 1
                // 该列只有 defaultExpression，不显式赋值则更新时不会刷新
                it[UserWallets.updatedAt] = LocalDateTime.now()
            }
            // 赠送余额同样是资金，必须入流水，否则钱包余额无法与流水逐笔对平。
            // 本金余额本次未变，故 amount 为 0、before/after 取同值。
            recordWalletTransaction(
                userId = referrerId,
                changeType = "referral_reward",
                amount = BigDecimal.ZERO,
                balanceBefore = wallet[UserWallets.balance],
                balanceAfter = wallet[UserWallets.balance],
                bonusAmount = bonus,
                bonusBefore = bonusBefore,
                bonusAfter = bonusAfter,
                reason = "推荐首消奖励",
                relatedId = consumptionRecordId
            )
        } else {
            logger.warn("[ReferralReward] 推荐人 $referrerId 无钱包记录，跳过 $bonus 元赠送余额发放。")
        }

        // P1-8: 记录推荐人注册奖励
        ReferralRewards.insert {
            it[ReferralRewards.relationId] = relationId
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = "points"
            it[ReferralRewards.rewardValue] = BigDecimal(registrationPoints)
            it[ReferralRewards.stage] = "registration"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = now
        }

        // P1-8: 记录被推荐人注册奖励
        ReferralRewards.insert {
            it[ReferralRewards.relationId] = relationId
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = "points"
            it[ReferralRewards.rewardValue] = BigDecimal(refereeRegistrationPoints)
            it[ReferralRewards.stage] = "registration"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = now
        }

        // 记录推荐人首消奖励
        ReferralRewards.insert {
            it[ReferralRewards.relationId] = relationId
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = "points_balance"
            it[ReferralRewards.rewardValue] = BigDecimal(points).add(bonus)
            it[ReferralRewards.stage] = "first_consumption"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = now
        }

        // 标记首消完成，同时作为「后续消费走持续奖励」的判定依据
        ReferralRelations.update({ ReferralRelations.id eq relationId }) {
            it[ReferralRelations.firstConsumedAt] = now
            it[ReferralRelations.rewardStatus] = "full"
        }

        val couponValue = grantNewcomerCoupon(refereeId)

        // 需求 3.6.1 + P1-7：推荐奖励发放时 → 微信订阅消息，降级站内信
        val totalReferrerPoints = registrationPoints + points
        val rewardContent = buildString {
            append("您推荐的好友已完成首次消费，获得注册奖励 $registrationPoints 积分 + 首消奖励 $points 积分")
            if (wallet != null) append("、${bonus.setScale(2, RoundingMode.HALF_UP)} 元赠送余额")
            append("。")
        }
        ScheduledNotifier.notify(
            userId = referrerId,
            templateKey = "referral_reward",
            title = "推荐奖励到账",
            content = rewardContent,
            subscribeData = mapOf(
                "thing1" to "推荐奖励",
                "amount2" to "$totalReferrerPoints 积分" + (if (wallet != null) " + ${bonus.setScale(2, RoundingMode.HALF_UP)} 元" else "")
            ),
            page = "/pages/referral/index",
            msgType = "activity",
            relatedId = consumptionRecordId
        )

        // P1-8: 被推荐人注册奖励通知
        sendNotification(
            userId = refereeId,
            title = "推荐注册奖励到账",
            content = "您通过推荐注册获得 $refereeRegistrationPoints 积分奖励。",
            msgType = "activity",
            relatedId = consumptionRecordId
        )

        if (couponValue != null) {
            sendNotification(
                userId = refereeId,
                title = "新人诊疗券已到账",
                content = "感谢您的首次消费，${couponValue.setScale(2, RoundingMode.HALF_UP)} 元新人诊疗券" +
                    "已发放至「我的优惠券」，请在有效期内使用。",
                msgType = "activity",
                relatedId = consumptionRecordId
            )
        }

        checkAndAwardTierReward(referrerId, consumptionRecordId)

        return ReferralRewardOutcome.FirstConsumption(
            referrerId = referrerId,
            points = points,
            bonusBalance = if (wallet != null) bonus.toDouble() else 0.0,
            newcomerCouponValue = couponValue?.toDouble()
        )
    }

    /**
     * 持续奖励：被推荐人首消之后，每消费满 1000 元，推荐人获 10 积分。
     *
     * 按「单笔实付金额」取整计算，不跨笔累计——单笔不足 1000 元的部分不发奖。
     */
    private suspend fun grantOngoingReward(
        referrerId: Long,
        refereeId: Long,
        payAmount: BigDecimal,
        consumptionRecordId: Long?
    ): ReferralRewardOutcome {
        val steps = payAmount.divideToIntegralValue(BigDecimal(ONGOING_AMOUNT_STEP)).toInt()
        if (steps <= 0) {
            return ReferralRewardOutcome.NoReward(referrerId, "本笔实付未达 $ONGOING_AMOUNT_STEP 元")
        }

        // 每档积分数可由管理端在「推荐活动管理」调整（规则 actionType 与积分来源同名），未配置时取需求默认值
        val pointsPerStep = findRule(SOURCE_ONGOING)?.get(PointsRules.pointsPerUnit)
            ?: ONGOING_POINTS_PER_STEP

        val desired = steps * pointsPerStep
        val points = awardCappedPoints(
            userId = referrerId,
            desired = desired,
            sourceType = SOURCE_ONGOING,
            description = "推荐好友消费持续奖励",
            sourceId = consumptionRecordId
        )
        if (points <= 0) {
            return ReferralRewardOutcome.NoReward(referrerId, "本月推荐积分已达上限")
        }

        ReferralRewards.insert {
            it[ReferralRewards.relationId] = 0
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = refereeId
            it[ReferralRewards.rewardType] = "points"
            it[ReferralRewards.rewardValue] = BigDecimal(points)
            it[ReferralRewards.stage] = "ongoing"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = LocalDateTime.now()
        }

        sendNotification(
            userId = referrerId,
            title = "推荐持续奖励到账",
            content = "您推荐的好友本次消费 ${payAmount.setScale(2, RoundingMode.HALF_UP)} 元，您获得 $points 积分。",
            msgType = "activity",
            relatedId = consumptionRecordId
        )

        return ReferralRewardOutcome.Ongoing(referrerId = referrerId, points = points)
    }

    /**
     * 阶梯奖励：累计推荐满 5 人时一次性追加 200 积分，同一推荐人只发一次。
     */
    private suspend fun checkAndAwardTierReward(referrerId: Long, consumptionRecordId: Long?) {
        val referralCount = ReferralRelations.selectAll()
            .where { ReferralRelations.referrerId eq referrerId }
            .toList()
            .count { row -> row[ReferralRelations.referrerId] != row[ReferralRelations.refereeId] }

        val threshold = tierReferralThreshold()
        if (referralCount < threshold) return

        val alreadyGranted = ReferralRewards.selectAll()
            .where {
                (ReferralRewards.referrerId eq referrerId) and (ReferralRewards.stage eq "tier")
            }
            .count() > 0
        if (alreadyGranted) return

        val rewardPoints = tierRewardPoints()
        val points = awardCappedPoints(
            userId = referrerId,
            desired = rewardPoints,
            sourceType = SOURCE_TIER,
            description = "推荐满${threshold}人阶梯奖励",
            sourceId = consumptionRecordId
        )

        ReferralRewards.insert {
            it[ReferralRewards.relationId] = 0
            it[ReferralRewards.referrerId] = referrerId
            it[ReferralRewards.refereeId] = 0
            it[ReferralRewards.rewardType] = "points"
            it[ReferralRewards.rewardValue] = BigDecimal(points)
            it[ReferralRewards.stage] = "tier"
            it[ReferralRewards.status] = "granted"
            it[ReferralRewards.grantedAt] = LocalDateTime.now()
        }

        sendNotification(
            userId = referrerId,
            title = "推荐阶梯奖励到账",
            content = "累计推荐满 $threshold 人，额外获得 $points 积分。",
            msgType = "activity",
            relatedId = consumptionRecordId
        )
    }

    /**
     * 给被推荐人发放新人诊疗券。
     *
     * @return 发放成功返回券面额；模板未配置、已停用或库存发完时返回 null（只告警，不阻断消费结算）。
     */
    private suspend fun grantNewcomerCoupon(refereeId: Long): BigDecimal? {
        val template = CouponTemplates.selectAll()
            .where { (CouponTemplates.name eq NEWCOMER_COUPON_NAME) and (CouponTemplates.status eq 1) }
            .toList()
            .firstOrNull()

        if (template == null) {
            logger.warn("[ReferralReward] 未配置启用的「$NEWCOMER_COUPON_NAME」模板，跳过用户 $refereeId 的新人券发放。")
            return null
        }

        val templateId = template[CouponTemplates.id]
        val issued = template[CouponTemplates.issuedQuantity]
        val total = template[CouponTemplates.totalQuantity]
        if (total > 0 && issued >= total) {
            logger.warn("[ReferralReward]「$NEWCOMER_COUPON_NAME」库存已发完（$issued/$total），跳过发放。")
            return null
        }

        UserCoupons.insert {
            it[UserCoupons.userId] = refereeId
            it[UserCoupons.templateId] = templateId
            it[UserCoupons.couponCode] = "NEW${refereeId}${System.currentTimeMillis().toString().takeLast(8)}"
            it[UserCoupons.status] = "unused"
        }
        CouponTemplates.update({ CouponTemplates.id eq templateId }) {
            it[CouponTemplates.issuedQuantity] = issued + 1
        }

        return template[CouponTemplates.faceValue]
    }

    /**
     * 发放积分并施加月度上限（需求 3.5.2：每月最多通过推荐获得 500 积分，防止刷量）。
     *
     * @return 实际发放的积分数；可能被上限截断，为 0 表示本月额度已用尽。
     */
    private suspend fun awardCappedPoints(
        userId: Long,
        desired: Int,
        sourceType: String,
        description: String,
        sourceId: Long?
    ): Int {
        if (desired <= 0) return 0

        // 双倍奖励活动期内翻倍（仅积分，赠余额/新人券不参与），翻倍后仍受月度上限约束
        val effective = if (ReferralActivityConfig.doubleBonusActive()) desired * 2 else desired

        // 上限与有效期取自 referral 规则，便于管理端「推荐活动管理」在线调整
        val rule = findRule("referral")
        val limit = rule?.get(PointsRules.monthlyLimit) ?: DEFAULT_MONTHLY_POINTS_LIMIT
        val validityMonths = rule?.get(PointsRules.validityMonths) ?: DEFAULT_VALIDITY_MONTHS

        val grantedThisMonth = referralPointsGrantedThisMonth(userId)
        val remaining = limit - grantedThisMonth
        if (remaining <= 0) {
            logger.info("[ReferralReward] 用户 $userId 本月推荐积分已达上限 $limit，跳过 $sourceType。")
            return 0
        }

        val actual = minOf(effective, remaining)
        if (actual < effective) {
            logger.info("[ReferralReward] 用户 $userId 本月推荐积分额度不足，$sourceType 由 $effective 截断为 $actual。")
        }

        addPointsToUser(
            userId = userId,
            amount = actual,
            sourceType = sourceType,
            description = description,
            validityMonths = validityMonths,
            sourceId = sourceId
        )
        return actual
    }

    /** 统计本月已通过推荐渠道发放给该用户的积分总数。 */
    private suspend fun referralPointsGrantedThisMonth(userId: Long): Int {
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()
        return PointsRecords.selectAll()
            .where {
                (PointsRecords.userId eq userId) and
                    (PointsRecords.sourceType inList REFERRAL_SOURCE_TYPES) and
                    (PointsRecords.createdAt greaterEq monthStart)
            }
            .toList()
            .sumOf { it[PointsRecords.changeAmount] }
    }

    /** 首消奖励积分数，管理端可配置；未配置时取需求默认值 100。 */
    private suspend fun firstConsumptionPoints(): Int =
        findRule(SOURCE_FIRST_CONSUMPTION)?.get(PointsRules.pointsPerUnit)
            ?: DEFAULT_FIRST_CONSUMPTION_POINTS

    /**
     * 按 actionType 查找已启用的积分规则。
     *
     * 显式按 id 升序取第一条：库里可能同时存在播种规则与 test-data.sql 插入的同名规则，
     * 不指定顺序时命中哪一条是不确定的，会造成奖励额度随机漂移。
     */
    private suspend fun findRule(actionType: String): ResultRow? =
        PointsRules.selectAll()
            .where { (PointsRules.actionType eq actionType) and (PointsRules.status eq 1) }
            .orderBy(PointsRules.id, SortOrder.ASC)
            .toList()
            .firstOrNull()

    companion object {
        const val SOURCE_REGISTRATION = "referral_registration"
        const val SOURCE_FIRST_CONSUMPTION = "referral_first_consumption"
        const val SOURCE_TIER = "referral_tier_reward"
        const val SOURCE_ONGOING = "referral_ongoing"

        /** 月度上限按这些来源合并统计 */
        val REFERRAL_SOURCE_TYPES = listOf(
            SOURCE_REGISTRATION,
            SOURCE_FIRST_CONSUMPTION,
            SOURCE_TIER,
            SOURCE_ONGOING
        )

        /** 被推荐人首消后获得的新人券模板名称 */
        const val NEWCOMER_COUPON_NAME = "推荐新人券"

        /** P1-8: 推荐人注册奖励积分 (需求 3.5.2) */
        private const val REGISTRATION_REFERRER_POINTS = 20

        /** P1-8: 被推荐人注册奖励积分 (需求 3.5.2) */
        private const val REFEREE_REGISTRATION_POINTS = 50

        /** 持续奖励：每消费满 1000 元发 10 积分 */
        private const val ONGOING_AMOUNT_STEP = 1000
        private const val ONGOING_POINTS_PER_STEP = 10

        /** 阶梯奖励：累计推荐满 5 人追加 200 积分 */
        private const val TIER_REFERRAL_THRESHOLD = 5
        private const val TIER_REWARD_POINTS = 200

        /** 首消奖励兜底值（PointsRules 未配置 referral_first_consumption 时生效） */
        private const val DEFAULT_FIRST_CONSUMPTION_POINTS = 100
        private val FIRST_CONSUMPTION_BONUS = BigDecimal("50")

        /** 推荐奖励月度积分上限兜底值 */
        private const val DEFAULT_MONTHLY_POINTS_LIMIT = 500

        /** 积分有效期兜底值（月） */
        private const val DEFAULT_VALIDITY_MONTHS = 12

        /**
         * P2-17: 读取可配置的推荐首消赠送余额（元）。
         */
        private suspend fun firstConsumptionBonusYuan(): BigDecimal = try {
            val value = com.dentalclinic.common.config.ReferralActivityConfig.getInt(
                "referral.first_consumption_bonus_yuan", 50
            )
            BigDecimal(value)
        } catch (e: Exception) {
            FIRST_CONSUMPTION_BONUS
        }

        /**
         * P2-17: 读取可配置的阶梯奖励人数阈值。
         */
        private suspend fun tierReferralThreshold(): Int = try {
            com.dentalclinic.common.config.ReferralActivityConfig.getInt(
                "referral.tier_threshold_count", TIER_REFERRAL_THRESHOLD
            )
        } catch (e: Exception) {
            TIER_REFERRAL_THRESHOLD
        }

        /**
         * P2-17: 读取可配置的阶梯奖励积分数。
         */
        private suspend fun tierRewardPoints(): Int = try {
            com.dentalclinic.common.config.ReferralActivityConfig.getInt(
                "referral.tier_reward_points", TIER_REWARD_POINTS
            )
        } catch (e: Exception) {
            TIER_REWARD_POINTS
        }
    }
}

/** 推荐奖励结算结果，供调用方汇总到响应或日志中。 */
sealed class ReferralRewardOutcome {
    /** 被消费用户没有推荐关系，无需发奖 */
    object NoRelation : ReferralRewardOutcome()

    /** 首消奖励：本笔是该被推荐人的首次消费 */
    data class FirstConsumption(
        val referrerId: Long,
        val points: Int,
        val bonusBalance: Double,
        val newcomerCouponValue: Double?
    ) : ReferralRewardOutcome()

    /** 持续奖励：首消之后按每满 1000 元发 10 积分 */
    data class Ongoing(
        val referrerId: Long,
        val points: Int
    ) : ReferralRewardOutcome()

    /** 未达持续奖励门槛，或本月推荐积分额度已用尽 */
    data class NoReward(
        val referrerId: Long,
        val reason: String
    ) : ReferralRewardOutcome()
}
