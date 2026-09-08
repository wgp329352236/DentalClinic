package com.dentalclinic.user

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
    val appointmentService = AppointmentService()
    val medicalRecordService = MedicalRecordService()
    val consumptionService = ConsumptionRecordService()
    val rechargeService = RechargeService()
    val pointsService = PointsService()
    val referralService = ReferralService()
    val couponService = CouponService()
    val notificationService = NotificationService()
    val fileUploadService = FileUploadService()

    routing {
        // Public auth endpoints
        userAuthRoutes(authService)

        // Protected user APIs
        userProfileRoutes()
        userWalletRoutes()
        userRechargeRoutes(rechargeService)
        userAppointmentRoutes(appointmentService)
        userMedicalRecordRoutes(medicalRecordService)
        userConsumptionRoutes(consumptionService)
        userPointsRoutes(pointsService)
        userReferralRoutes(referralService)
        userCouponRoutes(couponService)
        userNotificationRoutes(notificationService)
        fileUploadRoutes(fileUploadService)
        testReferralRoutes(referralService)
    }
}
