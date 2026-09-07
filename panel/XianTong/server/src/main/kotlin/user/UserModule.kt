package com.dentalclinic.user

import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.Departments
import com.dentalclinic.admin.entity.Doctors
import com.dentalclinic.admin.entity.DoctorClinicRels
import com.dentalclinic.admin.entity.DoctorSchedules
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.UserMemberLevels
import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.entity.ReferralRelations
import com.dentalclinic.admin.entity.ReferralRewards
import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.admin.entity.Notifications
import com.dentalclinic.admin.entity.MessageTemplates
import com.dentalclinic.admin.entity.UploadedFiles
import com.dentalclinic.config.DBConfig
import com.dentalclinic.user.route.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserModule")

/**
 * User (Mini-program) Module Entry Point
 */
fun Application.userModule() {
    // Create user tables if not exist
    runBlocking {
        suspendTransaction(DBConfig.database) {
            SchemaUtils.create(
                Clinics,
                Departments,
                Doctors,
                DoctorClinicRels,
                DoctorSchedules,
                Users,
                UserWallets,
                RechargeRecords,
                MemberLevels,
                UserMemberLevels,
                ConsumptionRecords,
                Appointments,
                PointsRules,
                PointsRecords,
                UserPointsSummary,
                ReferralRelations,
                ReferralRewards,
                CouponTemplates,
                UserCoupons,
                Notifications,
                MessageTemplates,
                UploadedFiles
            )
            logger.info("User tables created/verified.")
        }
    }

    // Register routes
    userAuthRoutes()
    userProfileRoutes()
    userAppointmentRoutes()
    userMedicalRecordRoutes()
    userConsumptionRoutes()
    userWalletRoutes()
    userRechargeRoutes()
    userPointsRoutes()
    userReferralRoutes()
    testReferralRoutes()
    userCouponRoutes()
    userNotificationRoutes()
    fileUploadRoutes()

    logger.info("User module initialized with all routes.")
}
