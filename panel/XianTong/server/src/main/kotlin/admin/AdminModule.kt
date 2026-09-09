package com.dentalclinic.admin


import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.route.*
import com.dentalclinic.admin.service.*
import com.dentalclinic.admin.util.*
import com.dentalclinic.common.config.DBConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.*
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
                RechargeRecords,
                MemberLevels,
                UserMemberLevels,
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
                // Files
                UploadedFiles
            )
            logger.info("All tables (admin + dental clinic) created/verified.")
        }
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
            logger.error("Unhandled exception in admin API", cause)
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

    // Business data services
    val clinicService = ClinicService()
    val departmentService = DepartmentService()
    val doctorService = DoctorService()
    val scheduleService = ScheduleService()
    val appointmentService = AppointmentAdminService()
    val customerService = CustomerService(pointsService)

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

            // Business data management routes
            clinicRoutes(clinicService)
            departmentRoutes(departmentService)
            doctorRoutes(doctorService)
            scheduleRoutes(scheduleService)
            appointmentAdminRoutes(appointmentService)
            customerRoutes(customerService)
        }
    }
}
