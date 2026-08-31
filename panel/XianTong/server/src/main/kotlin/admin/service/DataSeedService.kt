package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.AdminUsers
import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.config.DBConfig
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

/**
 * Data seeding service — inserts default data on first startup.
 */
object DataSeedService {

    private val logger = LoggerFactory.getLogger(DataSeedService::class.java)

    suspend fun seed() {
        suspendTransaction(DBConfig.database) {
            seedMemberLevels()
            seedPointsRules()
            seedDefaultAdmin()
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

    private suspend fun seedPointsRules() {
        val count = PointsRules.selectAll().count()
        if (count > 0) return

        val rules = listOf(
            Triple("消费积分", "earn", "consumption") to Pair(1, "每消费1元"),
            Triple("充值积分", "earn", "recharge") to Pair(5, "每充值1元"),
            Triple("每日签到", "earn", "signup") to Pair(2, "每日限1次"),
            Triple("完善信息", "earn", "profile_complete") to Pair(50, "一次性奖励"),
            Triple("就诊评价", "earn", "review") to Pair(10, "每月限4次"),
            Triple("推荐注册", "earn", "referral") to Pair(50, "被推荐人完成首次消费后发放"),
        )

        for ((info, pointsAndDesc) in rules) {
            val (name, ruleType, actionType) = info
            val (points, desc) = pointsAndDesc
            PointsRules.insert {
                it[PointsRules.ruleName] = name
                it[PointsRules.ruleType] = ruleType
                it[PointsRules.actionType] = actionType
                it[PointsRules.pointsPerUnit] = points
                it[PointsRules.unitDescription] = desc
                it[PointsRules.dailyLimit] = if (actionType == "signup") 1 else null
                it[PointsRules.monthlyLimit] = if (actionType == "review") 4 else null
                it[PointsRules.validityMonths] = 12
                it[PointsRules.status] = 1
            }
        }
        logger.info("[DataSeed]: Seeded ${rules.size} points rules.")
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
}
