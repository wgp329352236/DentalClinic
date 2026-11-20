package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.common.config.DBConfig
import com.dentalclinic.common.config.GlobalConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

// Helper function for R2DBC (no firstOrNull)
private fun <T> List<T>.firstOrNone(): T? = if (isEmpty()) null else this[0]

/**
 * Data seeding service — inserts default data on first startup.
 */
object DataSeedService {

    private val logger = LoggerFactory.getLogger(DataSeedService::class.java)

    /** 推荐奖励月度积分上限（需求 3.5.2：每月最多通过推荐获得 500 积分） */
    private const val REFERRAL_MONTHLY_POINTS_LIMIT = 500

    suspend fun seed() {
        suspendTransaction(DBConfig.database) {
            seedMemberLevels()
            seedRechargeTiers()
            seedPointsRules()
            seedReferralRewardRules()
            seedPointsGoods()
            seedAppointmentConfigs()
            seedReferralActivityConfigs()
            seedPointsDeductConfigs()
            seedMessageTemplates()
            seedBannerActivities()
            seedDemoContent()
            seedDefaultAdmin()
            seedSuperAdminRole()
            seedBusinessPermissions()
            seedAdminMenus()
        }
        logger.info("[DataSeed]: Default data seeded successfully.")
    }

    private suspend fun seedMemberLevels() {
        val count = MemberLevels.selectAll().count()
        if (count > 0) return // Already seeded

        val levels = listOf(
            Triple(1, "普通会员", 0.0),
            Triple(2, "银卡会员", 3000.0),
            Triple(3, "金卡会员", 10000.0),
            Triple(4, "钻石会员", 30000.0)
        )
        val discounts = mapOf(1 to 1.0, 2 to 0.95, 3 to 0.90, 4 to 0.85)
        val benefitsMap = mapOf(
            1 to """{"benefits":["预约优先","生日祝福"]}""",
            2 to """{"benefits":["专属客服","优先排队","预约优先","生日祝福"]}""",
            3 to """{"benefits":["免费年度口腔检查1次","洁牙优惠","专属客服","优先排队","预约优先","生日祝福"]}""",
            4 to """{"benefits":["免费年度口腔检查2次","家属共享折扣","VIP专属活动","免费年度口腔检查1次","洁牙优惠","专属客服","优先排队","预约优先","生日祝福"]}"""
        )

        for ((code, name, minConsumption) in levels) {
            MemberLevels.insert {
                it[MemberLevels.levelCode] = code
                it[MemberLevels.levelName] = name
                it[MemberLevels.minConsumption] = minConsumption.toBigDecimal()
                it[MemberLevels.discountRate] = discounts[code]!!.toBigDecimal()
                it[MemberLevels.benefits] = benefitsMap[code]
                it[MemberLevels.status] = 1
            }
        }
        logger.info("[DataSeed]: Seeded ${levels.size} member levels.")
    }

    /**
     * 播种充值档位默认值，与 RechargeService 旧硬编码 4 档保持一致：
     * 1000→10%、3000→15%、5000→20%、10000→25%。
     * 表非空即整体跳过：管理端调整过的配置不被覆盖。
     */
    private suspend fun seedRechargeTiers() {
        val count = RechargeTiers.selectAll().count()
        if (count > 0) return

        val tiers = listOf(
            RechargeTierSeed("充1000送10%", 1000.0, 0.1000, 10),
            RechargeTierSeed("充3000送15%", 3000.0, 0.1500, 20),
            RechargeTierSeed("充5000送20%", 5000.0, 0.2000, 30),
            RechargeTierSeed("充10000送25%", 10000.0, 0.2500, 40)
        )
        for ((name, minAmount, rate, sortOrder) in tiers) {
            RechargeTiers.insert {
                it[RechargeTiers.tierName] = name
                it[RechargeTiers.minAmount] = minAmount.toBigDecimal()
                it[bonusRate] = rate.toBigDecimal()
                it[status] = 1
                it[RechargeTiers.sortOrder] = sortOrder
            }
        }
        logger.info("[DataSeed]: Seeded ${tiers.size} recharge tiers.")
    }

    private data class RechargeTierSeed(
        val tierName: String, val minAmount: Double, val bonusRate: Double, val sortOrder: Int
    )

    /**
     * 积分规则播种默认值。
     *
     * 比率以「每 [unitAmount] 个单位积 [pointsPerUnit] 分」表达，使需求 3.4.1 的
     * 「每充值 1 元积 0.5 分」这类小数比率无需把整型列改成 decimal：
     * unitAmount=2 + pointsPerUnit=1 即「每 2 元 1 分」，与 0.5 分/元等价。
     */
    private data class PointsRuleSeed(
        val ruleName: String,
        val actionType: String,
        val pointsPerUnit: Int,
        val unitDescription: String,
        val unitAmount: Int = 1,
        val dailyLimit: Int? = null
    )

    // referral 的月度上限由 seedReferralRewardRules 统一补，避免同一职责分散在两处
    private val pointsRuleSeeds = listOf(
        PointsRuleSeed("消费积分", "consumption", 1, "每消费1元"),
        PointsRuleSeed(
            ruleName = "充值积分",
            actionType = "recharge",
            pointsPerUnit = 1,
            unitDescription = "每充值2元积1分（即0.5分/元），仅按实付金额计",
            unitAmount = 2
        ),
        PointsRuleSeed("每日签到", "signup", 2, "每日限1次", dailyLimit = 1),
        PointsRuleSeed("完善信息", "profile_complete", 50, "一次性奖励"),
        PointsRuleSeed("就诊评价", "review", 10, "每月限4次"),
        PointsRuleSeed("推荐注册", "referral", 50, "被推荐人完成首次消费后发放")
    )

    /**
     * 播种积分规则（需求 3.4）。
     *
     * 原先用「表非空即整体跳过」判重，导致两类问题无法自愈：
     * - 老库补不上后续新增的规则；
     * - 播种值写错也永远改不回来——充值积分曾播成 5 分/元（与需求 0.5 分/元相差 10 倍），
     *   而 test-data.sql 插入的那行更是 0 分/元，意味着充值永远不发积分。
     *
     * 现改为按 actionType 逐条幂等补齐，并修复「比率非正」的行：pointsPerUnit 或 unitAmount
     * 非正时规则恒不发放积分，属明确的损坏状态，修复它不会覆盖任何有效的运营配置；
     * 比率为正的行一律不动，那是管理端设的值。
     */
    private suspend fun seedPointsRules() {
        val rowsByActionType = PointsRules.selectAll()
            .toList()
            .groupBy { it[PointsRules.actionType] }

        for (seed in pointsRuleSeeds) {
            val rows = rowsByActionType[seed.actionType].orEmpty()

            if (rows.isEmpty()) {
                PointsRules.insert {
                    it[PointsRules.ruleName] = seed.ruleName
                    it[PointsRules.ruleType] = "earn"
                    it[PointsRules.actionType] = seed.actionType
                    it[PointsRules.pointsPerUnit] = seed.pointsPerUnit
                    it[PointsRules.unitAmount] = seed.unitAmount
                    it[PointsRules.unitDescription] = seed.unitDescription
                    it[PointsRules.dailyLimit] = seed.dailyLimit
                    it[PointsRules.monthlyLimit] = if (seed.actionType == "review") 4 else null
                    it[PointsRules.validityMonths] = 12
                    it[PointsRules.status] = 1
                }
                logger.info("[DataSeed]: Created points rule: ${seed.actionType}")
                continue
            }

            // 逐行判定而非按 actionType 批量更新：库中若仍有历史重复行，只修坏的那条
            for (row in rows) {
                val pointsPerUnit = row[PointsRules.pointsPerUnit]
                val unitAmount = row[PointsRules.unitAmount]
                if (pointsPerUnit > 0 && unitAmount > 0) continue

                PointsRules.update({ PointsRules.id eq row[PointsRules.id] }) {
                    it[PointsRules.pointsPerUnit] = seed.pointsPerUnit
                    it[PointsRules.unitAmount] = seed.unitAmount
                    it[PointsRules.unitDescription] = seed.unitDescription
                }
                logger.warn(
                    "[DataSeed]: Repaired points rule ${seed.actionType} (id=${row[PointsRules.id]}): " +
                        "invalid ratio $pointsPerUnit 分/$unitAmount 单位 -> " +
                        "${seed.pointsPerUnit} 分/${seed.unitAmount} 单位."
                )
            }
        }
    }

    /**
     * 补齐推荐奖励相关的积分规则（需求 3.5.2）。
     *
     * 按 actionType 逐条幂等补齐，并给既有 referral 规则补上月度上限。
     */
    private suspend fun seedReferralRewardRules() {
        val existingActionTypes = PointsRules.selectAll()
            .toList()
            .map { it[PointsRules.actionType] }
            .toSet()

        val referralRules = listOf(
            Triple("referral_first_consumption", "推荐首消奖励", 100) to "被推荐人完成首次消费时发放",
            Triple("referral_ongoing", "推荐持续奖励", 10) to "被推荐人每消费满1000元"
        )

        for ((info, desc) in referralRules) {
            val (actionType, name, points) = info
            if (actionType in existingActionTypes) continue
            PointsRules.insert {
                it[PointsRules.ruleName] = name
                it[PointsRules.ruleType] = "earn"
                it[PointsRules.actionType] = actionType
                it[PointsRules.pointsPerUnit] = points
                it[PointsRules.unitDescription] = desc
                it[PointsRules.validityMonths] = 12
                it[PointsRules.status] = 1
            }
            logger.info("[DataSeed]: Created points rule: $actionType")
        }

        // 月度上限挂在 referral 这条总规则上，由 ReferralRewardService 统一对全部推荐奖励生效。
        // 只补尚未配置的行：库中可能存在多条同名规则（播种 + test-data.sql），逐行判定避免覆盖管理端已设值。
        val capped = PointsRules.update({
            (PointsRules.actionType eq "referral") and PointsRules.monthlyLimit.isNull()
        }) {
            it[PointsRules.monthlyLimit] = REFERRAL_MONTHLY_POINTS_LIMIT
        }
        if (capped > 0) {
            logger.info("[DataSeed]: Set referral monthly limit to $REFERRAL_MONTHLY_POINTS_LIMIT on $capped rule(s).")
        }
    }

    /**
     * 播种积分商城默认商品（需求 3.4.2）：
     * - 500 积分兑换口腔检查（服务类，不限量）
     * - 2000 积分兑换电动牙刷（实物类，不限量）
     * 按 name 幂等：管理端改名后的行不会被重复插入，也不覆盖管理端调价/库存配置。
     */
    private data class PointsGoodsSeed(
        val name: String,
        val goodsType: String,
        val pointsPrice: Int,
        val sortOrder: Int,
        val description: String
    )

    private suspend fun seedPointsGoods() {
        val existingNames = PointsGoods.selectAll().toList()
            .map { it[PointsGoods.name] }
            .toSet()

        val goodsSeeds = listOf(
            PointsGoodsSeed(
                name = "口腔检查（单次）",
                goodsType = "service",
                pointsPrice = 500,
                sortOrder = 10,
                description = "需求 3.4.2：500 积分兑换一次口腔检查服务，到店出示核销码即可使用。"
            ),
            PointsGoodsSeed(
                name = "电动牙刷",
                goodsType = "item",
                pointsPrice = 2000,
                sortOrder = 20,
                description = "需求 3.4.2：2000 积分兑换电动牙刷一支，到店出示核销码领取。"
            )
        )

        for (seed in goodsSeeds) {
            if (seed.name in existingNames) continue
            PointsGoods.insert {
                it[PointsGoods.name] = seed.name
                it[PointsGoods.goodsType] = seed.goodsType
                it[PointsGoods.pointsPrice] = seed.pointsPrice
                it[PointsGoods.sortOrder] = seed.sortOrder
                it[PointsGoods.description] = seed.description
                it[PointsGoods.totalStock] = -1
                it[PointsGoods.status] = 1
            }
            logger.info("[DataSeed]: Created points goods: ${seed.name}")
        }
    }

    /**
     * 播种预约规则配置（需求 3.2.4）。
     *
     * 把原先硬编码的取消提前分钟数、爽约次数阈值、爽约窗口天数、是否允许当天预约
     * 落到 admin_system_config 表，管理端「系统设置 → 系统配置」可直接修改。
     * 幂等：按 configKey 判重，已存在则跳过（不覆盖管理端调过的值）。
     */
    private suspend fun seedAppointmentConfigs() {
        data class Cfg(val key: String, val value: String, val desc: String)
        val seeds = listOf(
            Cfg(
                key = "appointment.cancel_deadline_minutes",
                value = "120",
                desc = "自助取消/改约需提前的分钟数（默认120=2小时）"
            ),
            Cfg(
                key = "appointment.no_show_limit",
                value = "3",
                desc = "爽约次数阈值，达到后禁止在线预约（0=关闭该限制）"
            ),
            Cfg(
                key = "appointment.no_show_window_days",
                value = "30",
                desc = "爽约统计窗口天数"
            ),
            Cfg(
                key = "appointment.allow_same_day",
                value = "1",
                desc = "是否允许当天预约（1=允许，0=不允许）"
            )
        )
        for (seed in seeds) {
            val exists = AdminSystemConfigs.selectAll()
                .where { AdminSystemConfigs.configKey eq seed.key }
                .count() > 0
            if (exists) continue
            AdminSystemConfigs.insert {
                it[configKey] = seed.key
                it[configValue] = seed.value
                it[configType] = "int"
                it[category] = "appointment"
                it[description] = seed.desc
                it[minValue] = 0
                it[maxValue] = null
            }
            logger.info("[DataSeed]: Created appointment config: ${seed.key} = ${seed.value}")
        }
    }

    /**
     * 播种推荐活动配置（需求 4.8：限时双倍奖励活动）。
     *
     * 默认关闭（enabled=0），由管理端「系统设置 → 系统配置」按需开启并设定起止日期。
     * 幂等：按 configKey 判重，已存在则跳过（不覆盖管理端已配置的活动值）。
     */
    private suspend fun seedReferralActivityConfigs() {
        data class Cfg(val key: String, val value: String, val type: String, val desc: String)
        val seeds = listOf(
            Cfg(
                key = "referral.double_bonus_enabled",
                value = "0",
                type = "int",
                desc = "是否启用推荐双倍奖励活动（1=启用，0=关闭）"
            ),
            Cfg(
                key = "referral.double_bonus_start",
                value = "",
                type = "string",
                desc = "双倍奖励活动开始日期（含当天，yyyy-MM-dd）"
            ),
            Cfg(
                key = "referral.double_bonus_end",
                value = "",
                type = "string",
                desc = "双倍奖励活动结束日期（含当天，yyyy-MM-dd）"
            )
        )
        for (seed in seeds) {
            val exists = AdminSystemConfigs.selectAll()
                .where { AdminSystemConfigs.configKey eq seed.key }
                .count() > 0
            if (exists) continue
            AdminSystemConfigs.insert {
                it[configKey] = seed.key
                it[configValue] = seed.value
                it[configType] = seed.type
                it[category] = "referral"
                it[description] = seed.desc
                it[minValue] = null
                it[maxValue] = null
            }
            logger.info("[DataSeed]: Created referral activity config: ${seed.key}")
        }
    }

    /**
     * 播种积分抵扣诊疗费配置（需求 3.4.2）。
     *
     * 把「是否启用 / 多少积分抵 1 元 / 单笔最高抵扣比例」落到 admin_system_config，
     * 管理端「系统设置 → 系统配置」可直接改。默认按需求值：100 积分抵 1 元、单笔最高抵 20%。
     * 幂等：按 configKey 判重，已存在则跳过（不覆盖管理端调过的值）。
     */
    private suspend fun seedPointsDeductConfigs() {
        data class Cfg(val key: String, val value: String, val desc: String)
        val seeds = listOf(
            Cfg(
                key = "points.deduct_enabled",
                value = "1",
                desc = "是否启用积分抵扣诊疗费（1=启用，0=关闭）"
            ),
            Cfg(
                key = "points.deduct_points_per_yuan",
                value = "100",
                desc = "积分抵扣比率：多少积分抵 1 元（默认 100）"
            ),
            Cfg(
                key = "points.deduct_max_ratio_percent",
                value = "20",
                desc = "单笔积分最高抵扣应付金额的比例（%，默认 20）"
            ),
            // P2-17: 推荐奖励可配置
            Cfg(
                key = "referral.first_consumption_bonus_yuan",
                value = "50",
                desc = "推荐首消奖励赠送余额（元，默认 50）"
            ),
            Cfg(
                key = "referral.tier_threshold_count",
                value = "5",
                desc = "推荐阶梯奖励人数阈值（默认 5 人）"
            ),
            Cfg(
                key = "referral.tier_reward_points",
                value = "200",
                desc = "推荐阶梯奖励积分数（默认 200 分）"
            )
        )
        for (seed in seeds) {
            val exists = AdminSystemConfigs.selectAll()
                .where { AdminSystemConfigs.configKey eq seed.key }
                .count() > 0
            if (exists) continue
            AdminSystemConfigs.insert {
                it[configKey] = seed.key
                it[configValue] = seed.value
                it[configType] = "int"
                it[category] = "points"
                it[description] = seed.desc
                it[minValue] = 0
                it[maxValue] = null
            }
            logger.info("[DataSeed]: Created points deduct config: ${seed.key} = ${seed.value}")
        }
    }

    /**
     * 播种微信订阅消息模板（需求 3.6：预约提醒 / 3.4.1：积分到期提醒）。
     *
     * template_id 先以空串占位：真实的微信模板 ID 需要在小程序后台申请模板后，
     * 由管理端「通知模板」配置填入。占位期间定时任务自动降级为站内信，不丢消息；
     * 管理端填入真实 ID 后无需改代码即可走订阅消息通道。
     *
     * trigger_event 与 template_key 同值：ScheduledNotifier 按 template_key 查模板，
     * 小程序公开配置接口按 trigger_event 取可申请的模板 ID，两列同值让一条种子同时满足两侧。
     * 幂等：按 templateKey 判重，不覆盖管理端已填的真实模板 ID。
     */
    private data class MessageTemplateSeed(
        val templateKey: String,
        val title: String,
        val content: String
    )

    private suspend fun seedMessageTemplates() {
        val existingKeys = MessageTemplates.selectAll().toList()
            .map { it[MessageTemplates.templateKey] }
            .toSet()

        val templateSeeds = listOf(
            MessageTemplateSeed(
                templateKey = "appointment_remind_evening",
                title = "就诊前一天提醒",
                content = "您已预约明天就诊，请按时到店；如需取消或改约，请至少提前2小时操作。"
            ),
            MessageTemplateSeed(
                templateKey = "appointment_remind_morning",
                title = "就诊当天提醒",
                content = "您今天有预约就诊，请携带有效证件准时到店签到。"
            ),
            MessageTemplateSeed(
                templateKey = "points_expire_remind",
                title = "积分到期提醒",
                content = "您有积分即将到期，到期后将自动清零，可前往积分商城兑换好礼。"
            ),
            MessageTemplateSeed(
                templateKey = "consumption_success",
                title = "消费成功通知",
                content = "您的消费已结算完成，可前往消费记录查看详情。"
            ),
            MessageTemplateSeed(
                templateKey = "level_change",
                title = "会员等级变动通知",
                content = "您的会员等级已更新，点击查看详情。"
            ),
            MessageTemplateSeed(
                templateKey = "referral_reward",
                title = "推荐奖励到账通知",
                content = "您有一笔推荐奖励已到账，点击查看详情。"
            ),
            MessageTemplateSeed(
                templateKey = "appointment_confirmed",
                title = "预约已确认",
                content = "您的预约已确认，请准时到院就诊。"
            ),
            MessageTemplateSeed(
                templateKey = "appointment_submitted",
                title = "预约已提交",
                content = "您的预约已提交，等待门店确认。"
            ),
            MessageTemplateSeed(
                templateKey = "appointment_cancelled",
                title = "预约已取消",
                content = "您的预约已取消，如需重新预约请在小程序操作。"
            ),
            MessageTemplateSeed(
                templateKey = "appointment_rejected",
                title = "预约申请未通过",
                content = "您的预约申请未通过，如有疑问请联系门店。"
            ),
            MessageTemplateSeed(
                templateKey = "recharge_success",
                title = "充值成功通知",
                content = "您的充值已到账，可前往钱包查看余额。"
            ),
            MessageTemplateSeed(
                templateKey = "activity_notice",
                title = "新活动上线通知",
                content = "门诊发布了新活动，点击查看活动详情。"
            )
        )

        for (seed in templateSeeds) {
            if (seed.templateKey in existingKeys) continue
            MessageTemplates.insert {
                it[MessageTemplates.templateKey] = seed.templateKey
                it[MessageTemplates.templateId] = ""
                it[MessageTemplates.title] = seed.title
                it[MessageTemplates.content] = seed.content
                it[MessageTemplates.triggerEvent] = seed.templateKey
                it[MessageTemplates.status] = 1

            }
            logger.info("[DataSeed]: Created message template: ${seed.templateKey}")
        }
    }

    /**
     * 存量轮播图补建活动（升级兼容）。
     *
     * 新模型要求一张轮播图对应一个活动。历史轮播图没有 activity_id，若不处理，
     * 升级后首页轮播会整体消失。这里按轮播图自身的标题/图片补建「待完善」活动并回填绑定，
     * 运营随后可在「内容管理 → 活动管理」补充详情。幂等：只处理 activity_id 为空的行。
     */
    private suspend fun seedBannerActivities() {
        val orphans = MpBanners.selectAll()
            .where { MpBanners.activityId.isNull() }
            .orderBy(MpBanners.id)
            .toList()
        if (orphans.isEmpty()) return

        orphans.forEach { row ->
            val activityId = MpActivities.insert {
                it[MpActivities.title] = row[MpBanners.title]
                it[MpActivities.coverImage] = row[MpBanners.imageUrl]
                it[MpActivities.summary] = "由历史轮播图自动补建，请在活动管理中补充活动详情"
                it[MpActivities.status] = 1
                it[MpActivities.sortOrder] = row[MpBanners.sortOrder]
                it[MpActivities.createdBy] = "system-backfill"
            } get MpActivities.id

            MpBanners.update({ MpBanners.id eq row[MpBanners.id] }) {
                it[MpBanners.activityId] = activityId
            }
        }
        logger.info("[DataSeed]: 为 ${orphans.size} 张历史轮播图补建了活动并完成绑定。")
    }

    /**
     * 本地演示内容（仅非生产、且活动与轮播图都为空时）：
     * 3 个活动 + 3 张已绑定活动的轮播图，用于走通「首页轮播 → 活动详情 → 立即预约」。
     * 图片直接用小程包内的 /images/banner-*.jpg，不依赖后台上传文件。
     */
    private suspend fun seedDemoContent() {
        if (GlobalConfig.isProd) return
        if (MpActivities.selectAll().count() > 0 || MpBanners.selectAll().count() > 0) return

        val start = LocalDateTime.now().minusDays(30)
        val end = LocalDateTime.now().plusDays(60)
        val seeds = listOf(
            Triple(
                "暑期正畸特惠 · 隐形矫正立减2000元",
                "/images/banner-ortho.jpg",
                "暑期矫正专场，隐形矫正立减 2000 元，含免费拍片与方案设计"
            ),
            Triple(
                "会员充值 · 最高赠送25%",
                "/images/banner-recharge.jpg",
                "充值最高赠送 25%，余额可用于全门店项目消费"
            ),
            Triple(
                "新客专享100元口腔检查券",
                "/images/banner-newcomer.jpg",
                "首次到院的新客可领取口腔检查券一张，需提前预约"
            )
        )

        seeds.forEachIndexed { index, (title, image, summary) ->
            val sortOrder = index + 1
            val activityId = MpActivities.insert {
                it[MpActivities.title] = title
                it[MpActivities.coverImage] = image
                it[MpActivities.summary] = summary
                it[MpActivities.content] =
                    "活动时间：${start.toLocalDate()} 至 ${end.toLocalDate()}\n\n" +
                        "演示数据：请在「内容管理 → 活动管理」中替换为真实活动规则。\n\n" +
                        "本活动需提前预约，具体以门店实际安排为准。"
                it[MpActivities.sortOrder] = sortOrder
                it[MpActivities.status] = 1
                it[MpActivities.startAt] = start
                it[MpActivities.endAt] = end
                it[MpActivities.createdBy] = "system-demo"
            } get MpActivities.id

            MpBanners.insert {
                it[MpBanners.title] = title
                it[MpBanners.imageUrl] = image
                it[MpBanners.activityId] = activityId
                it[MpBanners.sortOrder] = sortOrder
                it[MpBanners.status] = 1
                it[MpBanners.startAt] = start
                it[MpBanners.endAt] = end
                it[MpBanners.createdBy] = "system-demo"
            }
        }
        logger.info("[DataSeed]: 已写入 ${seeds.size} 组演示活动与首页轮播图（仅开发环境）。")
    }

    private suspend fun seedDefaultAdmin() {
        val count = AdminUsers.selectAll().count()
        if (count > 0) return

        val passwordHash = com.dentalclinic.admin.auth.PasswordEncoder.hash("admin123")
        AdminUsers.insert {
            it[AdminUsers.username] = "admin"
            it[AdminUsers.passwordHash] = passwordHash
            it[AdminUsers.displayName] = "系统管理员"
            it[AdminUsers.email] = "admin@dentalclinic.com"
            it[AdminUsers.status] = 1
        }
        logger.info("[DataSeed]: Seeded default admin user (username=admin, password=admin123).")
    }

    /**
     * 确保 super_admin 系统角色存在，并将默认 admin 用户关联到该角色。
     * 这是权限与菜单关联的前提：角色缺失时，seedBusinessPermissions、seedAdminMenus 的关联都会静默落空。
     * 幂等：角色与用户关联均先查后插。
     */
    private suspend fun seedSuperAdminRole() {
        // 1. 确保 super_admin 角色存在
        val existingRole = AdminRoles.selectAll()
            .where { AdminRoles.roleCode eq "super_admin" }
            .toList()
            .firstOrNone()
        val roleId = if (existingRole != null) {
            existingRole[AdminRoles.id]
        } else {
            val newRoleId = AdminRoles.insert {
                it[AdminRoles.roleCode] = "super_admin"
                it[AdminRoles.roleName] = "超级管理员"
                it[AdminRoles.description] = "系统内置超级管理员，拥有全部权限"
                it[AdminRoles.isSystem] = 1
            }[AdminRoles.id]
            logger.info("[DataSeed]: Created super_admin role.")
            newRoleId
        }

        // 2. 确保默认 admin 用户已关联到 super_admin
        val adminUser = AdminUsers.selectAll()
            .where { AdminUsers.username eq "admin" }
            .toList()
            .firstOrNone()
        if (adminUser != null) {
            val adminUserId = adminUser[AdminUsers.id]
            val linked = AdminUserRoles.selectAll()
                .where {
                    (AdminUserRoles.userId eq adminUserId) and
                    (AdminUserRoles.roleId eq roleId)
                }
                .toList()
            if (linked.isEmpty()) {
                AdminUserRoles.insert {
                    it[AdminUserRoles.userId] = adminUserId
                    it[AdminUserRoles.roleId] = roleId
                }
                logger.info("[DataSeed]: Linked admin user to super_admin role.")
            }
        }
    }

    private suspend fun seedBusinessPermissions() {
        // 完整权限目录：覆盖所有路由 requirePermission 用到的权限码（按模块分组）。
        // 幂等插入——已存在的权限码（含历史/运行时创建的）保持不变，仅补齐缺失项。
        val permissionCatalog = listOf(
            // 数据看板（管理端首页，需求 4.10）
            Triple("dashboard:view", "查看数据看板", "dashboard"),
            // 预约
            Triple("appointment:list", "查看预约", "appointment"),
            Triple("appointment:view", "查看预约详情", "appointment"),
            Triple("appointment:create", "代客预约", "appointment"),
            Triple("appointment:update", "更新预约状态", "appointment"),
            Triple("appointment:delete", "取消预约", "appointment"),
            // 客户
            Triple("customer:list", "查看客户", "customer"),
            Triple("customer:view", "查看客户详情", "customer"),
            Triple("customer:update", "更新客户信息", "customer"),
            // 客户标签
            Triple("customer_tag:list", "查看客户标签", "customer"),
            Triple("customer_tag:create", "创建客户标签", "customer"),
            Triple("customer_tag:update", "更新客户标签", "customer"),
            Triple("customer_tag:delete", "删除客户标签", "customer"),
            // 消费
            Triple("consumption:list", "查看消费流水", "consumption"),
            Triple("consumption:create", "录入消费", "consumption"),
            // 充值档位 / 会员等级 / 充值流水
            Triple("recharge:list", "查看充值流水", "recharge"),
            Triple("recharge_tier:list", "查看充值档位", "recharge"),
            Triple("recharge_tier:create", "创建充值档位", "recharge"),
            Triple("recharge_tier:update", "更新充值档位", "recharge"),
            Triple("recharge_tier:delete", "删除充值档位", "recharge"),
            Triple("member_level:list", "查看会员等级", "recharge"),
            Triple("member_level:create", "创建会员等级", "recharge"),
            Triple("member_level:update", "更新会员等级", "recharge"),
            Triple("member_level:delete", "删除会员等级", "recharge"),
            // 消息模板
            Triple("message_template:list", "查看消息模板", "notification"),
            Triple("message_template:create", "创建消息模板", "notification"),
            Triple("message_template:update", "更新消息模板", "notification"),
            Triple("message_template:delete", "删除消息模板", "notification"),
            // 数据范围：可见全部门店数据（无此权限的账号仅见授权门店）
            Triple("clinic:data:all", "全部门店数据", "clinic"),
            // 门诊
            Triple("clinic:list", "查看门诊", "clinic"),
            Triple("clinic:create", "创建门诊", "clinic"),
            Triple("clinic:update", "更新门诊", "clinic"),
            Triple("clinic:delete", "删除门诊", "clinic"),
            // 科室
            Triple("department:list", "查看科室", "department"),
            Triple("department:create", "创建科室", "department"),
            Triple("department:update", "更新科室", "department"),
            Triple("department:delete", "删除科室", "department"),
            // 医生
            Triple("doctor:list", "查看医生", "doctor"),
            Triple("doctor:create", "创建医生", "doctor"),
            Triple("doctor:update", "更新医生", "doctor"),
            Triple("doctor:delete", "删除医生", "doctor"),
            // 优惠券
            Triple("coupon:view", "查看优惠券", "coupon"),
            Triple("coupon:manage", "管理优惠券", "coupon"),
            // 积分
            Triple("points:view", "查看积分", "points"),
            Triple("points:manage", "管理积分", "points"),
            // 推荐活动
            Triple("referral:view", "查看推荐活动", "referral"),
            Triple("referral:manage", "管理推荐奖励", "referral"),
            // 小程序内容配置（轮播图/公告）
            Triple("content:view", "查看小程序内容", "content"),
            Triple("content:manage", "管理小程序内容", "content"),
            // 审计日志
            Triple("audit:view", "查看审计日志", "audit"),
            // 系统配置
            Triple("config:view", "查看配置", "config"),
            Triple("config:create", "创建配置", "config"),
            Triple("config:update", "更新配置", "config"),
            Triple("config:delete", "删除配置", "config"),
            // 菜单管理
            Triple("menu:list", "查看菜单", "menu"),
            Triple("menu:create", "创建菜单", "menu"),
            Triple("menu:update", "更新菜单", "menu"),
            Triple("menu:delete", "删除菜单", "menu"),
            // 角色管理
            Triple("role:list", "查看角色列表", "role"),
            Triple("role:create", "创建角色", "role"),
            Triple("role:update", "更新角色", "role"),
            Triple("role:delete", "删除角色", "role"),
            // 管理员账号
            Triple("user:list", "查看用户列表", "user"),
            Triple("user:create", "创建用户", "user"),
            Triple("user:update", "更新用户", "user"),
            Triple("user:delete", "删除用户", "user")
        )

        // 一次性载入已存在权限码，避免逐条查询
        val existingCodes = AdminPermissions.selectAll().toList()
            .map { it[AdminPermissions.permissionCode] }
            .toSet()
        for ((code, name, module) in permissionCatalog) {
            if (code !in existingCodes) {
                AdminPermissions.insert {
                    it[permissionCode] = code
                    it[permissionName] = name
                    it[AdminPermissions.module] = module
                }
                logger.info("[DataSeed]: Created permission: $code")
            }
        }

        // 将库中「全部」权限一次性授予 super_admin（幂等）：不仅覆盖本目录，
        // 也包含历史/运行时新增但未列入目录的权限码，确保超级管理员真正拿全。
        val superAdminRole = AdminRoles.selectAll()
            .where { AdminRoles.roleCode eq "super_admin" }
            .toList()
            .firstOrNone()
        if (superAdminRole == null) {
            logger.warn("[DataSeed]: super_admin 角色不存在，跳过权限授予。")
            return
        }
        val roleId = superAdminRole[AdminRoles.id]

        val grantedIds = AdminRolePermissions.selectAll()
            .where { AdminRolePermissions.roleId eq roleId }
            .toList()
            .map { it[AdminRolePermissions.permissionId] }
            .toSet()
        val allPermIds = AdminPermissions.selectAll().toList().map { it[AdminPermissions.id] }

        var newlyGranted = 0
        for (permId in allPermIds) {
            if (permId !in grantedIds) {
                AdminRolePermissions.insert {
                    it[AdminRolePermissions.roleId] = roleId
                    it[AdminRolePermissions.permissionId] = permId
                }
                newlyGranted++
            }
        }
        logger.info("[DataSeed]: super_admin 权限补齐完成，本次新增授予 $newlyGranted 项（累计 ${allPermIds.size} 项）。")
    }

    /**
     * 播种后台完整菜单目录（仅含有后端支撑的真实页面），并关联到 super_admin 角色。
     * 分组：工作台、诊所运营、客户运营、优惠券管理、推荐活动、通知管理、系统管理。
     * 刻意排除 game/queue/task 等无后端路由的模板遗留页（避免死链），
     * 以及 system/notification（顶层 notification 的重复旧版）。
     * 注意 dashboard 不属于模板遗留页：它由 DashboardRoutes 提供 7 个统计端点，是需求 4.10 的数据看板。
     * 幂等：按 route_path 判重，重复启动不产生脏数据。
     * 前端为 backend 菜单模式，component 存归一化视图路径（如 /system/user/index）。
     */
    private suspend fun seedAdminMenus() {
        // 工作台——管理端首页，对应需求 4.10「管理端首页展示核心运营数据看板」。
        // 与其余六个分组保持同构（目录 + 子页）：需求附录 B 的工作台含「数据看板、待办事项」两项，
        // 用目录留出待办事项的位置，也保证后端下发的 homePath=/dashboard 能由目录重定向到首个子页。
        val dashboardDirId = ensureMenu(
            parentId = null,
            menuName = "工作台",
            menuType = "directory",
            routePath = "/dashboard",
            component = null,
            permissionCode = null,
            icon = "lucide:layout-dashboard",
            sortOrder = 1
        )
        ensureMenu(dashboardDirId, "数据看板", "menu", "/dashboard/analytics", "/dashboard/analytics/index", "dashboard:view", "lucide:chart-line", 1)

        // 诊所运营（门诊/科室/医生/排班/预约）——核心业务
        val clinicOpsDirId = ensureMenu(
            parentId = null,
            menuName = "诊所运营",
            menuType = "directory",
            routePath = "/clinic-ops",
            component = null,
            permissionCode = null,
            icon = "lucide:hospital",
            sortOrder = 10
        )
        ensureMenu(clinicOpsDirId, "门诊管理", "menu", "/clinic-ops/clinic", "/clinic/index", "clinic:list", "lucide:building-2", 1)
        ensureMenu(clinicOpsDirId, "科室管理", "menu", "/clinic-ops/department", "/department/index", "department:list", "lucide:layout-grid", 2)
        ensureMenu(clinicOpsDirId, "医生管理", "menu", "/clinic-ops/doctor", "/doctor/index", "doctor:list", "lucide:stethoscope", 3)
        ensureMenu(clinicOpsDirId, "排班管理", "menu", "/clinic-ops/schedule", "/schedule/index", "doctor:list", "lucide:calendar-clock", 4)
        ensureMenu(clinicOpsDirId, "预约管理", "menu", "/clinic-ops/appointment", "/appointment/index", "appointment:list", "lucide:calendar-check", 5)
        ensureMenu(clinicOpsDirId, "预约看板", "menu", "/clinic-ops/appointment-board", "/appointment/board", "appointment:list", "lucide:calendar-range", 6)

        // 客户运营（客户/积分规则/积分记录）——核心业务
        val customerOpsDirId = ensureMenu(
            parentId = null,
            menuName = "客户运营",
            menuType = "directory",
            routePath = "/customer-ops",
            component = null,
            permissionCode = null,
            icon = "lucide:users",
            sortOrder = 30
        )
        ensureMenu(customerOpsDirId, "客户管理", "menu", "/customer-ops/customer", "/customer/index", "customer:list", "lucide:contact", 1)
        ensureMenu(customerOpsDirId, "客户标签", "menu", "/customer-ops/customer-tags", "/customer/tags", "customer_tag:list", "lucide:tags", 2)
        ensureMenu(customerOpsDirId, "积分规则", "menu", "/customer-ops/points-rules", "/points/rules", "points:view", "lucide:award", 3)
        ensureMenu(customerOpsDirId, "积分记录", "menu", "/customer-ops/points-records", "/points/records", "points:view", "lucide:coins", 4)
        ensureMenu(customerOpsDirId, "消费管理", "menu", "/customer-ops/consumption", "/consumption/index", "consumption:list", "lucide:receipt", 5)
        ensureMenu(customerOpsDirId, "充值档位", "menu", "/customer-ops/recharge-tier", "/member/tier", "recharge_tier:list", "lucide:layers", 6)
        ensureMenu(customerOpsDirId, "会员等级", "menu", "/customer-ops/member-level", "/member/level", "member_level:list", "lucide:crown", 7)
        ensureMenu(customerOpsDirId, "充值流水", "menu", "/customer-ops/recharge-records", "/recharge/records", "recharge:list", "lucide:credit-card", 8)

        // 优惠券管理
        val couponDirId = ensureMenu(
            parentId = null,
            menuName = "优惠券管理",
            menuType = "directory",
            routePath = "/coupon",
            component = null,
            permissionCode = null,
            icon = "lucide:ticket",
            sortOrder = 60
        )
        ensureMenu(couponDirId, "优惠券模板", "menu", "/coupon/index", "/coupon/index", "coupon:view", "lucide:ticket", 1)
        ensureMenu(couponDirId, "发放记录", "menu", "/coupon/record", "/coupon/record", "coupon:view", "lucide:list", 2)

        // 推荐活动
        val referralDirId = ensureMenu(
            parentId = null,
            menuName = "推荐活动",
            menuType = "directory",
            routePath = "/referral",
            component = null,
            permissionCode = null,
            icon = "lucide:share-2",
            sortOrder = 70
        )
        ensureMenu(referralDirId, "推荐关系", "menu", "/referral/index", "/referral/index", "referral:view", "lucide:network", 1)
        ensureMenu(referralDirId, "奖励记录", "menu", "/referral/reward", "/referral/reward", "referral:view", "lucide:gift", 2)

        // 内容管理（小程序首页轮播图与公告，需求 4.11）
        val contentDirId = ensureMenu(
            parentId = null,
            menuName = "内容管理",
            menuType = "directory",
            routePath = "/content",
            component = null,
            permissionCode = null,
            icon = "lucide:image",
            sortOrder = 75
        )
        ensureMenu(contentDirId, "活动管理", "menu", "/content/activity", "/content/activity/index", "content:view", "lucide:calendar-heart", 1)
        ensureMenu(contentDirId, "轮播图管理", "menu", "/content/banner", "/content/banner/index", "content:view", "lucide:images", 2)
        ensureMenu(contentDirId, "公告管理", "menu", "/content/notice", "/content/notice/index", "content:view", "lucide:megaphone", 3)

        // 通知管理（复用 config 权限；后端端点在 ConfigRoutes 的 /notifications*）
        val notificationDirId = ensureMenu(
            parentId = null,
            menuName = "通知管理",
            menuType = "directory",
            routePath = "/notification",
            component = null,
            permissionCode = null,
            icon = "lucide:bell",
            sortOrder = 80
        )
        ensureMenu(notificationDirId, "通知规则", "menu", "/notification/rules", "/notification/rules/index", "config:view", "lucide:bell-ring", 1)
        ensureMenu(notificationDirId, "通知历史", "menu", "/notification/history", "/notification/history/index", "config:view", "lucide:history", 2)
        ensureMenu(notificationDirId, "消息模板", "menu", "/notification/template", "/notification/template/index", "message_template:list", "lucide:file-text", 3)

        // 系统管理
        val systemDirId = ensureMenu(
            parentId = null,
            menuName = "系统管理",
            menuType = "directory",
            routePath = "/system",
            component = null,
            permissionCode = null,
            icon = "lucide:settings",
            sortOrder = 90
        )
        ensureMenu(systemDirId, "用户管理", "menu", "/system/user", "/system/user/index", "user:list", "lucide:users", 1)
        ensureMenu(systemDirId, "角色管理", "menu", "/system/role", "/system/role/index", "role:list", "lucide:shield", 2)
        ensureMenu(systemDirId, "菜单管理", "menu", "/system/menu", "/system/menu/index", "menu:list", "lucide:menu", 3)
        ensureMenu(systemDirId, "系统配置", "menu", "/system/config", "/system/config/index", "config:view", "lucide:cog", 4)
        ensureMenu(systemDirId, "审计日志", "menu", "/system/audit", "/system/audit/index", "audit:view", "lucide:scroll-text", 5)

        // 关联到 super_admin 角色
        linkMenusToSuperAdmin()
        logger.info("[DataSeed]: Seeded admin menus (dashboard/clinic-ops/customer-ops/system/notification/coupon/referral/content).")
    }

    /** 幂等创建菜单：按 routePath 判重，返回菜单ID（已存在则返回既有ID）。 */
    private suspend fun ensureMenu(
        parentId: Long?,
        menuName: String,
        menuType: String,
        routePath: String,
        component: String?,
        permissionCode: String?,
        icon: String?,
        sortOrder: Int
    ): Long {
        val existing = AdminMenus.selectAll()
            .where { AdminMenus.routePath eq routePath }
            .toList()
            .firstOrNone()
        if (existing != null) {
            return existing[AdminMenus.id]
        }
        return AdminMenus.insert {
            it[AdminMenus.parentId] = parentId
            it[AdminMenus.menuName] = menuName
            it[AdminMenus.menuType] = menuType
            it[AdminMenus.routePath] = routePath
            it[AdminMenus.component] = component
            it[AdminMenus.permissionCode] = permissionCode
            it[AdminMenus.icon] = icon
            it[AdminMenus.sortOrder] = sortOrder
            it[AdminMenus.isVisible] = 1
            it[AdminMenus.isCache] = 0
        }[AdminMenus.id]
    }

    /** 将全部后台菜单关联到 super_admin 角色（幂等）。角色不存在则跳过并告警。 */
    private suspend fun linkMenusToSuperAdmin() {
        val superAdminRole = AdminRoles.selectAll()
            .where { AdminRoles.roleCode eq "super_admin" }
            .toList()
            .firstOrNone()
        if (superAdminRole == null) {
            logger.warn("[DataSeed]: super_admin 角色不存在，跳过菜单关联；请在「菜单管理」中手动分配。")
            return
        }
        val roleId = superAdminRole[AdminRoles.id]
        val menuPaths = listOf(
            "/dashboard", "/dashboard/analytics",
            "/clinic-ops", "/clinic-ops/clinic", "/clinic-ops/department", "/clinic-ops/doctor", "/clinic-ops/schedule", "/clinic-ops/appointment", "/clinic-ops/appointment-board",
            "/customer-ops", "/customer-ops/customer", "/customer-ops/customer-tags", "/customer-ops/points-rules", "/customer-ops/points-records",
            "/customer-ops/consumption", "/customer-ops/recharge-tier", "/customer-ops/member-level", "/customer-ops/recharge-records",
            "/coupon", "/coupon/index", "/coupon/record",
            "/referral", "/referral/index", "/referral/reward",
            "/content", "/content/activity", "/content/banner", "/content/notice",
            "/notification", "/notification/rules", "/notification/history", "/notification/template",
            "/system", "/system/user", "/system/role", "/system/menu", "/system/config", "/system/audit"
        )
        for (path in menuPaths) {
            val menu = AdminMenus.selectAll()
                .where { AdminMenus.routePath eq path }
                .toList()
                .firstOrNone() ?: continue
            val menuId = menu[AdminMenus.id]
            val linked = AdminRoleMenus.selectAll()
                .where {
                    (AdminRoleMenus.roleId eq roleId) and
                    (AdminRoleMenus.menuId eq menuId)
                }
                .toList()
            if (linked.isEmpty()) {
                AdminRoleMenus.insert {
                    it[AdminRoleMenus.roleId] = roleId
                    it[AdminRoleMenus.menuId] = menuId
                }
                logger.info("[DataSeed]: Linked menu $path to super_admin")
            }
        }
    }
}
