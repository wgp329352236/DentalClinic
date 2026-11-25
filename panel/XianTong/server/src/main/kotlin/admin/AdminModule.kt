package com.dentalclinic.admin


import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.route.*
import com.dentalclinic.admin.service.*
import com.dentalclinic.admin.util.*
import com.dentalclinic.common.FileUploadService
import com.dentalclinic.common.ReferralRewardService
import com.dentalclinic.common.config.DBConfig
import com.dentalclinic.common.config.SchemaMigration
import com.dentalclinic.common.logging.requestIdOrNull
import com.dentalclinic.common.scheduler.AppointmentReminderService
import com.dentalclinic.common.scheduler.AutoNoShowService
import com.dentalclinic.common.scheduler.MemberLevelReviewService
import com.dentalclinic.common.scheduler.NotificationRuleEngine
import com.dentalclinic.common.scheduler.PointsExpireService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdminModule")

/**
 * Admin Panel 模块入口
 * 安装 StatusPages 异常处理，注册路由，配置静态文件
 */
fun Application.adminModule() {
    // 确保数据库连接已初始化
    DBConfig.database
    runBlocking {
        suspendTransaction(DBConfig.database) {
            SchemaUtils.create(
                // Admin tables
                AdminUsers,
                AdminRoles,
                AdminUserRoles,
                AdminUserClinics,
                AdminPermissions,
                AdminRolePermissions,
                AdminMenus,
                AdminRoleMenus,
                AdminAuditLogs,
                AdminNotificationRules,
                AdminNotificationHistories,
                AdminSystemConfigs,
                // Dental clinic business tables — basic data
                Clinics,
                Departments,
                Doctors,
                DoctorSchedules,
                DoctorClinicRels,
                // User / membership
                Users,
                UserWallets,
                WalletTransactions,
                RechargeRecords,
                RechargeTiers,
                MemberLevels,
                UserMemberLevels,
                CustomerTags,
                UserTagRels,
                CustomerClinicRelations,
                ConsumptionRecords,
                // Appointments
                Appointments,
                // Points
                PointsRules,
                PointsRecords,
                UserPointsSummary,
                // Referral
                ReferralRelations,
                ReferralRewards,
                // Coupons
                CouponTemplates,
                UserCoupons,
                // Notifications
                Notifications,
                MessageTemplates,
                // Miniprogram content (activity / banner / notice)
                MpActivities,
                MpBanners,
                MpNotices,
                // Points mall
                PointsGoods,
                PointsExchangeOrders,
                // Files
                UploadedFiles,
                // Scheduler bookkeeping
                SystemJobLogs
            )
            logger.info("All tables (admin + dental clinic) created/verified.")
        }
        // SchemaUtils.create() 只建缺失的表，不会给已存在的表补列，因此新增字段需走显式迁移
        SchemaMigration.migrate()
    }
    // Seed default data
    runBlocking {
        DataSeedService.seed()
    }
    installAdminStatusPages()
    adminRouting()
    configureAdminStaticFiles()
    // 启动数据清理定时任务，每24小时清理过期运维数据（审计日志>90天、任务历史>30天、DLQ已处理>30天、通知历史>30天）
    AdminCleanupService.start()
    // P1 业务定时任务：预约提醒（20:05/10:05）、自动爽约（每小时）、积分过期清零+30天提醒（每日）、会员年度复评（1/1）
    AppointmentReminderService.start()
    AutoNoShowService.start()
    PointsExpireService.start()
    MemberLevelReviewService.start()
    // P1-11: 通知规则执行引擎（每小时）
    NotificationRuleEngine.start()
}


/**
 * 安装 Admin 专用的 StatusPages 异常处理
 */
private fun Application.installAdminStatusPages() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse(
                    code = 400,
                    message = cause.message ?: "参数校验失败",
                    data = cause.fieldErrors.takeIf { it.isNotEmpty() }
                )
            )
        }
        exception<AuthenticationException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(code = 401, message = cause.message ?: "认证失败")
            )
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(code = 403, message = cause.message ?: "权限不足")
            )
        }
        // 业务异常「资源不存在」→ 404。
        // 必须用全限定名：下方显式 import 的 io.ktor.server.plugins.NotFoundException 与
        // com.dentalclinic.admin.util.NotFoundException 同名，会遮蔽各 Service 实际抛出的业务异常，
        // 使其落入兜底的 exception<Exception> 分支返回 500。
        exception<com.dentalclinic.admin.util.NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(code = 404, message = cause.message ?: "资源不存在")
            )
        }
        // Ktor 内置的同名异常（如静态资源缺失）仍按 404 返回，避免退化成 500
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(code = 404, message = cause.message ?: "资源不存在")
            )
        }
        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<Unit>(code = 409, message = cause.message ?: "资源冲突")
            )
        }
        // Handle Ktor content negotiation / deserialization failures (malformed JSON, missing fields)
        exception<BadRequestException> { call, cause ->
            logger.warn("Bad request: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(code = 400, message = cause.message ?: "请求格式错误")
            )
        }
        exception<SerializationException> { call, cause ->
            logger.warn("Serialization error: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(code = 400, message = "请求体解析失败: ${cause.message}")
            )
        }
        exception<ContentTransformationException> { call, cause ->
            logger.warn("Content transformation error: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(code = 400, message = cause.message ?: "请求内容转换失败")
            )
        }
        exception<Exception> { call, cause ->
            logger.error(
                "Unhandled exception in admin API ${call.request.httpMethod.value} ${call.request.path()}" +
                    " requestId=${call.requestIdOrNull()}",
                cause
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse<Unit>(code = 500, message = "服务器内部错误")
            )
        }
    }
}

/**
 * Admin 路由注册
 */
fun Application.adminRouting() {
    val authService = AdminAuthService()
    val userService = AdminUserService()
    val roleService = AdminRoleService()
    val menuService = AdminMenuService()
    val dashboardService = AdminDashboardService()
    val pointsService = AdminPointsService()
    val couponService = AdminCouponService()
    val referralService = AdminReferralService()

    // 推荐奖励发放（user 端注册链路与 admin 端消费录入链路共用）
    val referralRewardService = ReferralRewardService()
    val consumptionService = ConsumptionAdminService(pointsService, referralRewardService)

    // Business data services
    val clinicService = ClinicService()
    // 门店签到码签发（与 user 端验签共用同一密钥配置，两个实例签名等价）
    val checkinCodeService = com.dentalclinic.common.security.CheckinCodeService()
    val departmentService = DepartmentService()
    val doctorService = DoctorService()
    val scheduleService = ScheduleService()
    val appointmentService = AppointmentAdminService()
    val customerService = CustomerService(pointsService)

    // P2 管理完整性：充值档位/会员等级/充值流水/客户标签/消息模板
    val rechargeTierAdminService = RechargeTierAdminService()
    val memberLevelAdminService = MemberLevelAdminService()
    val rechargeRecordAdminService = RechargeRecordAdminService()
    val customerTagAdminService = CustomerTagAdminService()
    val messageTemplateAdminService = MessageTemplateAdminService()

    // 小程序内容配置（轮播图/公告）与管理端文件上传
    val contentService = AdminContentService()
    val fileUploadService = FileUploadService()

    // P1-9: 积分商城核销台
    val adminPointsMallService = AdminPointsMallService()

    routing {
        // Admin API
        route("/api/admin") {
            install(AuditPlugin)
            authRoutes(authService)
            vbenRoutes() // Vben Admin 适配接口 (/user/info, /auth/codes, /menu/all)
            userRoutes(userService)
            roleRoutes(roleService)
            menuRoutes(menuService)
            dashboardRoutes(dashboardService)
            configRoutes()
            auditRoutes()
            pointsRoutes(pointsService)
            couponRoutes(couponService)
            referralRoutes(referralService)
            consumptionRoutes(consumptionService)

            // Business data management routes
            clinicRoutes(clinicService, checkinCodeService)
            departmentRoutes(departmentService)
            doctorRoutes(doctorService)
            scheduleRoutes(scheduleService)
            appointmentAdminRoutes(appointmentService)
            customerRoutes(customerService)

            // P2 管理完整性
            rechargeTierRoutes(rechargeTierAdminService)
            memberLevelRoutes(memberLevelAdminService)
            rechargeRecordRoutes(rechargeRecordAdminService)
            customerTagRoutes(customerTagAdminService)
            messageTemplateRoutes(messageTemplateAdminService)

            // P1-9: 积分商城核销台
            adminPointsMallRoutes(adminPointsMallService)

            // Miniprogram content config & file upload
            contentAdminRoutes(contentService)
            adminFileRoutes(fileUploadService)
        }
    }
}
