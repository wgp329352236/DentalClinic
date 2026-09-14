package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.*
import com.dentalclinic.common.config.DBConfig
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

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
            seedPointsRules()
            seedReferralRewardRules()
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
            Triple("appointment:update", "更新预约状态", "appointment"),
            Triple("appointment:delete", "取消预约", "appointment"),
            // 客户
            Triple("customer:list", "查看客户", "customer"),
            Triple("customer:view", "查看客户详情", "customer"),
            Triple("customer:update", "更新客户信息", "customer"),
            // 消费
            Triple("consumption:list", "查看消费流水", "consumption"),
            Triple("consumption:create", "录入消费", "consumption"),
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
        ensureMenu(customerOpsDirId, "积分规则", "menu", "/customer-ops/points-rules", "/points/rules", "points:view", "lucide:award", 2)
        ensureMenu(customerOpsDirId, "积分记录", "menu", "/customer-ops/points-records", "/points/records", "points:view", "lucide:coins", 3)
        ensureMenu(customerOpsDirId, "消费管理", "menu", "/customer-ops/consumption", "/consumption/index", "consumption:list", "lucide:receipt", 4)

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
        ensureMenu(contentDirId, "轮播图管理", "menu", "/content/banner", "/content/banner/index", "content:view", "lucide:images", 1)
        ensureMenu(contentDirId, "公告管理", "menu", "/content/notice", "/content/notice/index", "content:view", "lucide:megaphone", 2)

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
            "/clinic-ops", "/clinic-ops/clinic", "/clinic-ops/department", "/clinic-ops/doctor", "/clinic-ops/schedule", "/clinic-ops/appointment",
            "/customer-ops", "/customer-ops/customer", "/customer-ops/points-rules", "/customer-ops/points-records",
            "/customer-ops/consumption",
            "/coupon", "/coupon/index", "/coupon/record",
            "/referral", "/referral/index", "/referral/reward",
            "/content", "/content/banner", "/content/notice",
            "/notification", "/notification/rules", "/notification/history",
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
