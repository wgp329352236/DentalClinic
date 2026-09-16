package com.dentalclinic.user

import com.dentalclinic.common.FileUploadService
import com.dentalclinic.common.ReferralRewardService
import com.dentalclinic.common.security.CheckinCodeService
import com.dentalclinic.user.route.*
import com.dentalclinic.user.service.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserModule")

/**
 * User (C-end patient) Module Entry Point.
 *
 * Responsibilities: register all user-facing routes.
 * Table creation and StatusPages are managed by AdminModule (shared infrastructure).
 */
fun Application.userModule() {
    userRouting()
    logger.info("User module initialized.")
}

/**
 * User route registration — mirrors AdminModule.adminRouting() structure.
 */
fun Application.userRouting() {
    // Service instances (consistent with AdminModule pattern)
    val authService = UserAuthService()
    val profileService = UserProfileService()
    // 门店签到码验签；与 AdminModule 里签发实例读同一密钥配置，签名/验签等价
    val checkinCodeService = CheckinCodeService()
    val appointmentService = AppointmentService(checkinCodeService)
    val medicalRecordService = MedicalRecordService()
    val consumptionService = ConsumptionRecordService()
    val rechargeService = RechargeService()
    val pointsService = PointsService()
    val pointsMallService = PointsMallService()
    // 推荐奖励发放与 admin 端消费录入链路共用同一实现（common 包）
    val referralRewardService = ReferralRewardService()
    val referralService = ReferralService(pointsService, referralRewardService)
    val couponService = CouponService()
    val notificationService = NotificationService()
    val fileUploadService = FileUploadService()
    // 首页轮播图/公告，管理端在 AdminContentService 里维护
    val contentService = ContentService()

    routing {
        // Public auth endpoints
        userAuthRoutes(authService)

        // Public content endpoints (首页未登录也要展示轮播图与公告)
        userContentRoutes(contentService)

        // Protected user APIs
        userProfileRoutes(profileService)
        userWalletRoutes()
        userRechargeRoutes(rechargeService)
        userAppointmentRoutes(appointmentService)
        userMedicalRecordRoutes(medicalRecordService)
        userConsumptionRoutes(consumptionService)
        userPointsRoutes(pointsService)
        userPointsMallRoutes(pointsMallService)
        userReferralRoutes(referralService)
        userCouponRoutes(couponService)
        userNotificationRoutes(notificationService)
        fileUploadRoutes(fileUploadService)
        testReferralRoutes(referralService)
    }
}
