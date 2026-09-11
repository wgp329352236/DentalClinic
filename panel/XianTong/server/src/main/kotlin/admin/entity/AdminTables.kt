package com.dentalclinic.admin.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

// ============================================================
// Admin User
// ============================================================
object AdminUsers : Table("admin_user") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 50)
    val passwordHash = varchar("password_hash", 100)
    val displayName = varchar("display_name", 100)
    val email = varchar("email", 100).nullable()
    val avatar = varchar("avatar", 255).nullable()
    val status = integer("status").default(1)
    val lastLoginAt = datetime("last_login_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_uk_username", username)
    }
}

// ============================================================
// Admin Role
// ============================================================
object AdminRoles : Table("admin_role") {
    val id = long("id").autoIncrement()
    val roleCode = varchar("role_code", 50)
    val roleName = varchar("role_name", 100)
    val description = varchar("description", 255).nullable()
    val isSystem = integer("is_system").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_role_uk_role_code", roleCode)
    }
}

// ============================================================
// Admin User-Role
// ============================================================
object AdminUserRoles : Table("admin_user_role") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val roleId = long("role_id")
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_ur_uk_user_role", userId, roleId)
        index("admin_role_idx_role_id", false, roleId)
    }
}

// ============================================================
// Admin Permission
// ============================================================
object AdminPermissions : Table("admin_permission") {
    val id = long("id").autoIncrement()
    val permissionCode = varchar("permission_code", 100)
    val permissionName = varchar("permission_name", 100)
    val module = varchar("module", 50)
    val description = varchar("description", 255).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_perm_uk_permission_code", permissionCode)
        index("admin_perm_idx_module", false, module)
    }
}

// ============================================================
// Admin Role-Permission
// ============================================================
object AdminRolePermissions : Table("admin_role_permission") {
    val id = long("id").autoIncrement()
    val roleId = long("role_id")
    val permissionId = long("permission_id")
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_rp_uk_role_permission", roleId, permissionId)
        index("admin_perm_idx_permission_id", false, permissionId)
    }
}

// ============================================================
// Admin Menu
// ============================================================
object AdminMenus : Table("admin_menu") {
    val id = long("id").autoIncrement()
    val parentId = long("parent_id").nullable()
    val menuName = varchar("menu_name", 100)
    val menuType = varchar("menu_type", 20) // directory, menu, button
    val routePath = varchar("route_path", 200).nullable()
    val component = varchar("component", 200).nullable()
    val permissionCode = varchar("permission_code", 100).nullable()
    val icon = varchar("icon", 100).nullable()
    val sortOrder = integer("sort_order").default(0)
    val isVisible = integer("is_visible").default(1)
    val isCache = integer("is_cache").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("admin_menu_idx_parent_id", false, parentId)
        index("admin_menu_idx_sort", false, sortOrder)
    }
}

// ============================================================
// Admin Role-Menu
// ============================================================
object AdminRoleMenus : Table("admin_role_menu") {
    val id = long("id").autoIncrement()
    val roleId = long("role_id")
    val menuId = long("menu_id")
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_rm_uk_role_menu", roleId, menuId)
        index("admin_role_menu_idx_menu_id", false, menuId)
    }
}

// ============================================================
// Admin Audit Log
// ============================================================
object AdminAuditLogs : Table("admin_audit_log") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").nullable()
    val username = varchar("username", 50)
    val operationType = varchar("operation_type", 20)
    val targetResource = varchar("target_resource", 100)
    val targetId = varchar("target_id", 100).nullable()
    val requestParams = text("request_params").nullable()
    val responseCode = integer("response_code").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = varchar("user_agent", 255).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("admin_audit_admin_audit_idx_admin_audit_user_id", false, userId)
        index("admin_audit_admin_audit_idx_admin_audit_op_type", false, operationType)
        index("admin_audit_admin_audit_idx_admin_audit_target", false, targetResource)
        index("admin_audit_admin_audit_idx_admin_audit_created_at", false, createdAt)
    }
}

// ============================================================
// Admin Notification Rule
// ============================================================
object AdminNotificationRules : Table("admin_notification_rule") {
    val id = long("id").autoIncrement()
    val ruleName = varchar("rule_name", 100)
    val conditionType = varchar("condition_type", 50)
    val threshold = integer("threshold")
    val webhookUrl = varchar("webhook_url", 500)
    val isEnabled = integer("is_enabled").default(1)
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

// ============================================================
// Admin Notification History
// ============================================================
object AdminNotificationHistories : Table("admin_notification_history") {
    val id = long("id").autoIncrement()
    val ruleId = long("rule_id")
    val ruleName = varchar("rule_name", 100)
    val messageContent = text("message_content")
    val deliveryStatus = varchar("delivery_status", 20)
    val errorMessage = varchar("error_message", 500).nullable()
    val triggeredAt = datetime("triggered_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("admin_notif_hist_admin_notif_hist_idx_rule_id", false, ruleId)
        index("admin_notif_hist_admin_notif_hist_idx_triggered_at", false, triggeredAt)
    }
}

// ============================================================
// Admin System Config
// ============================================================
object AdminSystemConfigs : Table("admin_system_config") {
    val id = long("id").autoIncrement()
    val configKey = varchar("config_key", 100)
    val configValue = varchar("config_value", 500)
    val configType = varchar("config_type", 20).default("string")
    val category = varchar("category", 50)
    val description = varchar("description", 255).nullable()
    val isSensitive = integer("is_sensitive").default(0)
    val reloadType = varchar("reload_type", 20).default("hot")
    val minValue = integer("min_value").nullable()
    val maxValue = integer("max_value").nullable()
    val updatedBy = varchar("updated_by", 50).nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("admin_sc_uk_config_key", configKey)
        index("admin_syscfg_idx_category", false, category)
    }
}

// ============================================================
// Helper: CurrentTimestamp expression
// ============================================================
object CurrentTimestamp : org.jetbrains.exposed.v1.core.Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: org.jetbrains.exposed.v1.core.QueryBuilder) {
        queryBuilder.append("CURRENT_TIMESTAMP")
    }
}

// ============================================================
// Dental Clinic Business Tables
// ============================================================

// --- 基础数据 ---

/**
 * 门店表
 */
object Clinics : Table("clinic") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)           // 门店名称
    val address = varchar("address", 255)     // 详细地址
    val phone = varchar("phone", 20)          // 联系电话
    val openTime = varchar("open_time", 10)   // 营业开始时间 HH:mm
    val closeTime = varchar("close_time", 10) // 营业结束时间 HH:mm
    val businessDays = varchar("business_days", 50).default("1,2,3,4,5,6,7") // 营业星期 1-7
    val description = text("description").nullable() // 门店简介
    val coverImage = varchar("cover_image", 500).nullable() // 封面图URL
    val status = integer("status").default(1) // 1=营业中 0=停业
    val sortOrder = integer("sort_order").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("clinic_clinic_idx_status", false, status)
    }
}

/**
 * 科室/项目表
 */
object Departments : Table("department") {
    val id = long("id").autoIncrement()
    val clinicId = long("clinic_id")          // 所属门店
    val name = varchar("name", 100)           // 科室/项目名称
    val description = text("description").nullable()
    val status = integer("status").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("dept_dept_idx_dept_clinic_id", false, clinicId)
    }
}

/**
 * 医生表
 */
object Doctors : Table("doctor") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 50)            // 医生姓名
    val title = varchar("title", 50).nullable() // 职称
    val avatar = varchar("avatar", 500).nullable() // 头像URL
    val introduction = text("introduction").nullable() // 个人简介
    val specialties = varchar("specialties", 500).nullable() // 擅长领域（逗号分隔）
    val status = integer("status").default(1) // 1=在职 0=离职
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

/**
 * 医生排班表（按周循环）
 */
object DoctorSchedules : Table("doctor_schedule") {
    val id = long("id").autoIncrement()
    val doctorId = long("doctor_id")
    val dayOfWeek = integer("day_of_week")    // 1-7 周一至周日
    val startTime = varchar("start_time", 10) // 可预约开始时间 HH:mm
    val endTime = varchar("end_time", 10)     // 可预约结束时间 HH:mm
    val slotDuration = integer("slot_duration").default(30) // 时段长度（分钟）
    val maxAppointments = integer("max_appointments").default(1) // 每时段最大预约数
    val isEnabled = integer("is_enabled").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("doc_sched_doc_sched_idx_doctor_day", false, doctorId, dayOfWeek)
    }
}

/**
 * 医生-门店关联表（多对多）
 */
object DoctorClinicRels : Table("doctor_clinic_rel") {
    val id = long("id").autoIncrement()
    val doctorId = long("doctor_id")
    val clinicId = long("clinic_id")
    val isPrimary = integer("is_primary").default(0) // 是否主诊门店
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("doc_clinic_uk_doctor_clinic", doctorId, clinicId)
        index("doc_clinic_doc_clinic_idx_doc_clinic_rel_clinic_id", false, clinicId)
    }
}

// --- 用户/会员 ---

/**
 * C端用户表（患者）
 */
object Users : Table("user") {
    val id = long("id").autoIncrement()
    val openid = varchar("openid", 100)       // 微信openid
    val unionid = varchar("unionid", 100).nullable() // 微信unionid
    val phone = varchar("phone", 20).nullable() // 手机号
    val nickname = varchar("nickname", 100).nullable() // 昵称
    val realName = varchar("real_name", 50).nullable() // 真实姓名
    val gender = integer("gender").nullable() // 1=男 2=女
    val age = integer("age").nullable()
    val avatar = varchar("avatar", 500).nullable()
    val primaryClinicId = long("primary_clinic_id").nullable() // 首选就诊门店
    val sourceType = varchar("source_type", 20).default("self") // self/referral/store_entry
    val referredBy = long("referred_by").nullable() // 推荐人ID
    val status = integer("status").default(1) // 1=正常 0=禁用
    val lastLoginAt = datetime("last_login_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("cuser_uk_openid", openid)
        uniqueIndex("cuser_uk_phone", phone)
        index("cuser_user_idx_referred_by", false, referredBy)
        index("cuser_user_idx_created_at", false, createdAt)
    }
}

/**
 * 用户钱包表
 */
object UserWallets : Table("user_wallet") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val balance = decimal("balance", 12, 2).default(0.0.toBigDecimal()) // 实际支付余额
    val bonusBalance = decimal("bonus_balance", 12, 2).default(0.0.toBigDecimal()) // 赠送金额
    val version = integer("version").default(0) // 乐观锁版本号
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uwallet_uk_user_id", userId)
    }
}

/**
 * 钱包资金流水表
 *
 * 为什么不复用 admin_audit_log：审计日志由 AdminCleanupService 每日清理 90 天前的记录，
 * 而资金变动属财务凭证，需长期可追溯。故独立建表，只追加不修改。
 *
 * changeType 取值：
 *   recharge 充值入账、consume 消费扣款、adjust 管理端人工调整、refund 退款入账、
 *   referral_reward 推荐奖励发放赠送余额
 *
 * 覆盖要求：任何修改 user_wallet 余额或赠送余额的代码路径都必须写一条流水，
 * 否则「流水逐笔累加 = 钱包当前余额」这一对账等式不成立，表就失去意义。
 */
object WalletTransactions : Table("wallet_transaction") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val changeType = varchar("change_type", 20)
    val amount = decimal("amount", 12, 2)      // 本金余额变动（正=入账，负=出账）
    val bonusAmount = decimal("bonus_amount", 12, 2).default(0.0.toBigDecimal()) // 赠送余额变动
    val balanceBefore = decimal("balance_before", 12, 2)
    val balanceAfter = decimal("balance_after", 12, 2)
    val bonusBefore = decimal("bonus_before", 12, 2).default(0.0.toBigDecimal())
    val bonusAfter = decimal("bonus_after", 12, 2).default(0.0.toBigDecimal())
    val reason = varchar("reason", 200).nullable() // 变动原因（管理端人工调整必填）
    val operatorName = varchar("operator_name", 50).nullable() // 操作员；C 端自助操作为空
    val relatedId = long("related_id").nullable() // 关联业务ID（充值记录ID/消费记录ID）
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("wtx_wallet_transaction_idx_user_id", false, userId)
        index("wtx_wallet_transaction_idx_change_type", false, changeType)
        index("wtx_wallet_transaction_idx_created_at", false, createdAt)
    }
}

/**
 * 充值记录表
 */
object RechargeRecords : Table("recharge_record") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val clinicId = long("clinic_id")          // 操作门店
    val amount = decimal("amount", 12, 2)     // 充值金额
    val bonusAmount = decimal("bonus_amount", 12, 2).default(0.0.toBigDecimal()) // 赠送金额
    val paymentMethod = varchar("payment_method", 20).default("wechat") // wechat/balance
    val wechatOrderId = varchar("wechat_order_id", 100).nullable() // 微信支付订单号
    val status = varchar("status", 20).default("pending") // pending/success/failed/refunded
    val paidAt = datetime("paid_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("recharge_recharge_idx_admin_audit_user_id", false, userId)
        index("recharge_recharge_idx_status", false, status)
        index("recharge_recharge_idx_created_at", false, createdAt)
    }
}

/**
 * 会员等级配置表
 */
object MemberLevels : Table("member_level") {
    val id = long("id").autoIncrement()
    val levelCode = integer("level_code")     // 1-4
    val levelName = varchar("level_name", 50) // 普通会员/银卡/金卡/钻石
    val minConsumption = decimal("min_consumption", 12, 2) // 升级门槛（累计消费）
    val discountRate = decimal("discount_rate", 4, 2) // 折扣比例 如0.95表示95折
    val benefits = text("benefits").nullable() // 权益描述（JSON）
    val status = integer("status").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("mlevel_uk_level_code", levelCode)
    }
}

/**
 * 用户会员等级关联表
 */
object UserMemberLevels : Table("user_member_level") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val levelId = long("level_id")
    val accumulatedConsumption = decimal("accumulated_consumption", 12, 2).default(0.0.toBigDecimal()) // 累计消费
    val lastYearConsumption = decimal("last_year_consumption", 12, 2).default(0.0.toBigDecimal()) // 上一年度消费
    val effectiveFrom = datetime("effective_from") // 生效日期
    val effectiveTo = datetime("effective_to").nullable() // 失效日期
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("umember_uk_user_id", userId)
        index("umember_user_member_idx_level_id", false, levelId)
    }
}

/**
 * 消费记录表
 *
 * 金额字段口径（结算链路自上而下）：
 *   originalAmount 项目原价
 *     - discountAmount 会员等级折扣减免
 *   = amount 折后消费金额（营收统计口径，数据看板按此字段汇总）
 *     - couponDeduct 优惠券抵扣
 *   = payAmount 实付金额
 *     = balanceUsed 余额支付 + wechatPaid 微信支付
 *
 * 消费积分以 payAmount 为基数计算（需求 3.4.1：含余额支付和微信支付）。
 */
object ConsumptionRecords : Table("consumption_record") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val clinicId = long("clinic_id")
    val appointmentId = long("appointment_id").nullable() // 关联预约
    val amount = decimal("amount", 12, 2)     // 消费金额（会员折扣后）
    val originalAmount = decimal("original_amount", 12, 2).default(0.0.toBigDecimal()) // 项目原价
    val discountAmount = decimal("discount_amount", 12, 2).default(0.0.toBigDecimal()) // 会员折扣减免
    val couponDeduct = decimal("coupon_deduct", 12, 2).default(0.0.toBigDecimal()) // 优惠券抵扣金额
    val userCouponId = long("user_coupon_id").nullable() // 本次核销的用户优惠券ID
    val balanceUsed = decimal("balance_used", 12, 2).default(0.0.toBigDecimal()) // 使用余额
    val wechatPaid = decimal("wechat_paid", 12, 2).default(0.0.toBigDecimal()) // 微信支付部分
    val payAmount = decimal("pay_amount", 12, 2).default(0.0.toBigDecimal()) // 实付金额
    val pointsEarned = integer("points_earned").default(0) // 获得积分
    val status = varchar("status", 20).default("success") // success/refunded
    val operatorName = varchar("operator_name", 50).nullable() // 录入操作员（管理端账号）
    val description = text("description").nullable() // 消费说明
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("consumption_consumption_idx_admin_audit_user_id", false, userId)
        index("consumption_consumption_idx_clinic_id", false, clinicId)
        index("consumption_consumption_idx_created_at", false, createdAt)
    }
}

// --- 预约 ---

/**
 * 预约单表
 */
object Appointments : Table("appointment") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val clinicId = long("clinic_id")
    val doctorId = long("doctor_id")
    val departmentId = long("department_id").nullable() // 科室/项目
    val appointmentDate = date("appointment_date") // 预约日期
    val startTime = varchar("start_time", 10)   // 时段开始 HH:mm
    val endTime = varchar("end_time", 10)       // 时段结束 HH:mm
    val status = varchar("status", 20).default("pending") // pending/confirmed/arrived/completed/cancelled/no_show
    val cancelReason = text("cancel_reason").nullable()
    val cancelledBy = long("cancelled_by").nullable() // 取消操作人
    val cancelledAt = datetime("cancelled_at").nullable()
    val arrivedAt = datetime("arrived_at").nullable()
    val completedAt = datetime("completed_at").nullable()
    val noShowCount = integer("no_show_count").default(0) // 累计爽约次数
    val diagnosis = text("diagnosis").nullable()  // 诊断结果/电子病历摘要
    val treatmentPlan = text("treatment_plan").nullable() // 治疗方案
    val remark = text("remark").nullable()      // 备注
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("appt_appointment_idx_admin_audit_user_id", false, userId)
        index("appt_appointment_idx_clinic_doctor_date", false, clinicId, doctorId, appointmentDate)
        index("appt_appointment_idx_status", false, status)
        index("appt_appointment_idx_created_at", false, createdAt)
    }
}

// --- 积分 ---

/**
 * 积分规则配置表
 */
object PointsRules : Table("points_rule") {
    val id = long("id").autoIncrement()
    val ruleName = varchar("rule_name", 100)  // 规则名称
    val ruleType = varchar("rule_type", 20)   // earn/spend
    val actionType = varchar("action_type", 30) // consumption/recharge/signup/profile_complete/review/referral
    val pointsPerUnit = integer("points_per_unit") // 每 unitAmount 个单位获得的积分
    /**
     * 计分单位数量，与 [pointsPerUnit] 共同表达比率：每 unitAmount 个单位积 pointsPerUnit 分。
     *
     * 存在的理由：需求 3.4.1 的充值积分是「每充值 1 元积 0.5 分」，而 points_per_unit 是整型，
     * 存不下 0.5。用 unitAmount=2 + pointsPerUnit=1 表达同一比率（每 2 元 1 分），
     * 既精确表示小数积分率，又无需把整型列改成 decimal 而波及全部读取点。
     */
    val unitAmount = integer("unit_amount").default(1)
    val unitDescription = varchar("unit_description", 100).nullable() // 单位说明 如"每消费1元"
    val dailyLimit = integer("daily_limit").nullable() // 每日上限
    val monthlyLimit = integer("monthly_limit").nullable() // 每月上限
    val validityMonths = integer("validity_months").default(12) // 有效期（月）
    val status = integer("status").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

/**
 * 积分流水表
 */
object PointsRecords : Table("points_record") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val changeAmount = integer("change_amount") // 变动数量（正=获得，负=消耗）
    val balanceAfter = integer("balance_after") // 变动后余额
    val sourceType = varchar("source_type", 30) // 来源类型
    val sourceId = long("source_id").nullable() // 关联ID（如消费记录ID）
    val expiresAt = datetime("expires_at")      // 过期时间
    val description = text("description").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("precord_points_record_idx_admin_audit_user_id", false, userId)
        index("precord_points_record_idx_source_type", false, sourceType)
        index("precord_points_record_idx_expires_at", false, expiresAt)
        index("precord_points_record_idx_created_at", false, createdAt)
    }
}

/**
 * 用户积分汇总表（冗余计算，方便查询）
 */
object UserPointsSummary : Table("user_points_summary") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val totalBalance = integer("total_balance").default(0) // 当前总余额
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("upoints_uk_user_id", userId)
    }
}

// --- 推荐裂变 ---

/**
 * 推荐关系表
 */
object ReferralRelations : Table("referral_relation") {
    val id = long("id").autoIncrement()
    val referrerId = long("referrer_id")      // 推荐人
    val refereeId = long("referee_id")        // 被推荐人
    val referralCode = varchar("referral_code", 50) // 推荐码
    val registeredAt = datetime("registered_at") // 注册时间
    val firstConsumedAt = datetime("first_consumed_at").nullable() // 首次消费时间
    val rewardStatus = varchar("reward_status", 20).default("pending") // pending/partial/full
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ref_rel_uk_referee_id", refereeId)
        index("ref_rel_referral_rel_idx_referrer_id", false, referrerId)
        index("ref_rel_referral_rel_idx_referral_code", false, referralCode)
    }
}

/**
 * 推荐奖励记录表
 */
object ReferralRewards : Table("referral_reward") {
    val id = long("id").autoIncrement()
    val relationId = long("relation_id")      // 关联推荐关系
    val referrerId = long("referrer_id")
    val refereeId = long("referee_id")
    val rewardType = varchar("reward_type", 20) // points/balance/coupon
    val rewardValue = decimal("reward_value", 12, 2) // 奖励值
    val stage = varchar("stage", 20)          // registration/first_consumption/tier/ongoing
    val status = varchar("status", 20).default("pending") // pending/granted
    val grantedAt = datetime("granted_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("ref_reward_referral_reward_idx_referrer_id", false, referrerId)
        index("ref_reward_referral_reward_idx_stage", false, stage)
    }
}

// --- 优惠券 ---

/**
 * 优惠券模板表
 */
object CouponTemplates : Table("coupon_template") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)           // 券名称
    val couponType = varchar("coupon_type", 20) // discount/cash/service
    val faceValue = decimal("face_value", 12, 2).default(0.0.toBigDecimal()) // 面额
    val minConsumption = decimal("min_consumption", 12, 2).default(0.0.toBigDecimal()) // 使用门槛
    val applicableDepartments = text("applicable_departments").nullable() // 适用科室（JSON数组）
    val validFrom = datetime("valid_from")    // 生效时间
    val validTo = datetime("valid_to")        // 失效时间
    val totalQuantity = integer("total_quantity") // 发放总量
    val issuedQuantity = integer("issued_quantity").default(0) // 已发放数量
    val usedQuantity = integer("used_quantity").default(0) // 已使用数量
    val status = integer("status").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

/**
 * 用户优惠券表
 */
object UserCoupons : Table("user_coupon") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val templateId = long("template_id")
    val couponCode = varchar("coupon_code", 50) // 唯一券码
    val status = varchar("status", 20).default("unused") // unused/used/expired
    val usedAt = datetime("used_at").nullable()
    val appointmentId = long("appointment_id").nullable() // 使用时的预约ID
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ucoupon_uk_coupon_code", couponCode)
        index("ucoupon_user_coupon_idx_admin_audit_user_id", false, userId)
        index("ucoupon_user_coupon_idx_status", false, status)
    }
}

// --- 消息 ---

/**
 * 站内消息表
 */
object Notifications : Table("notification") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val title = varchar("title", 200)         // 消息标题
    val content = text("content")             // 消息内容
    val msgType = varchar("msg_type", 20)     // system/consumption/activity
    val isRead = integer("is_read").default(0) // 0=未读 1=已读
    val readAt = datetime("read_at").nullable()
    val relatedId = long("related_id").nullable() // 关联业务ID
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("notif_notification_idx_admin_audit_user_id", false, userId)
        index("notif_notification_idx_is_read", false, isRead)
        index("notif_notification_idx_created_at", false, createdAt)
    }
}

/**
 * 微信消息模板配置表
 */
object MessageTemplates : Table("message_template") {
    val id = long("id").autoIncrement()
    val templateKey = varchar("template_key", 50) // 模板标识
    val templateId = varchar("template_id", 100)  // 微信模板ID
    val title = varchar("title", 100)         // 模板标题
    val content = text("content")             // 模板内容示例
    val triggerEvent = varchar("trigger_event", 50) // 触发事件
    val status = integer("status").default(1) // 1=启用 0=停用
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("msg_tpl_uk_template_key", templateKey)
    }
}

// ============================================================
// Uploaded Files
// ============================================================
object UploadedFiles : Table("uploaded_file") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val fileName = varchar("file_name", 200)
    val originalName = varchar("original_name", 200)
    val fileSize = long("file_size")
    val contentType = varchar("content_type", 100).nullable()
    val url = varchar("url", 500)
    val createdAt = datetime("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_user_id", false, userId)
        uniqueIndex("uf_uk_file_name", fileName)
    }
}
