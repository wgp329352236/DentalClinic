package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Coupon service for user voucher management.
 */
class CouponService {

    /**
     * Get user's coupon list.
     */
    suspend fun getUserCoupons(userId: Long, status: String?): List<UserCouponDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val coupons = UserCoupons.selectAll()
                .where { UserCoupons.userId eq userId }
                .orderBy(UserCoupons.createdAt, SortOrder.DESC)
                .toList()

            val now = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            
            coupons.mapNotNull { couponRow ->
                val templateId = couponRow[UserCoupons.templateId]
                val template = CouponTemplates.selectAll()
                    .where { CouponTemplates.id eq templateId }
                    .toList()
                    .singleOrNull()

                if (template == null) return@mapNotNull null

                // Filter out expired coupons (status is still "unused" but past validTo)
                val validTo = template[CouponTemplates.validTo]
                if (couponRow[UserCoupons.status] == "unused" && now.isAfter(validTo)) {
                    return@mapNotNull null
                }

                val rowStatus = couponRow[UserCoupons.status]
                if (!status.isNullOrEmpty() && rowStatus != status) {
                    return@mapNotNull null
                }

                UserCouponDto(
                    id = couponRow[UserCoupons.id],
                    couponCode = couponRow[UserCoupons.couponCode],
                    templateName = template[CouponTemplates.name],
                    couponType = template[CouponTemplates.couponType],
                    faceValue = template[CouponTemplates.faceValue].toDouble(),
                    minConsumption = template[CouponTemplates.minConsumption].toDouble(),
                    validFrom = template[CouponTemplates.validFrom].format(formatter),
                    validTo = template[CouponTemplates.validTo].format(formatter),
                    status = rowStatus,
                    usedAt = couponRow[UserCoupons.usedAt]?.format(formatter)
                )
            }
        }
    }

    /**
     * Redeem (use) a coupon.
     */
    suspend fun redeemCoupon(userId: Long, couponCode: String, appointmentId: Long?): CouponRedeemResult {
        // Pre-checks outside transaction
        val couponCheck = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                UserCoupons.selectAll()
                    .where {
                        (UserCoupons.userId eq userId) and
                        (UserCoupons.couponCode eq couponCode)
                    }
                    .toList()
                    .singleOrNull()
            }
        }

        if (couponCheck == null) {
            return CouponRedeemResult.Error("优惠券不存在")
        }

        if (couponCheck[UserCoupons.status] != "unused") {
            return CouponRedeemResult.Error("优惠券已使用或已过期")
        }

        val templateId = couponCheck[UserCoupons.templateId]
        val template = withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                CouponTemplates.selectAll()
                    .where { CouponTemplates.id eq templateId }
                    .toList()
                    .singleOrNull()
            }
        }

        if (template == null) {
            return CouponRedeemResult.Error("优惠券模板不存在")
        }

        val now = LocalDateTime.now()
        if (now.isBefore(template[CouponTemplates.validFrom]) || now.isAfter(template[CouponTemplates.validTo])) {
            return CouponRedeemResult.Error("优惠券已过期")
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                // Mark as used
                UserCoupons.update({ UserCoupons.couponCode eq couponCode }) {
                    it[UserCoupons.status] = "used"
                    it[UserCoupons.usedAt] = now
                    if (appointmentId != null) {
                        it[UserCoupons.appointmentId] = appointmentId
                    }
                }

                // Update used quantity in template
                CouponTemplates.update({ CouponTemplates.id eq templateId }) {
                    it[CouponTemplates.usedQuantity] = template[CouponTemplates.usedQuantity] + 1
                }

                CouponRedeemResult.Success(
                    couponName = template[CouponTemplates.name],
                    faceValue = template[CouponTemplates.faceValue].toDouble()
                )
            }
        }
    }

    /**
     * Claim a coupon by template ID.
     */
    suspend fun claimCoupon(userId: Long, templateId: Long): CouponClaimResult {
        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                // Check if template exists and is available
                val template = CouponTemplates.selectAll()
                    .where { CouponTemplates.id eq templateId }
                    .toList()
                    .singleOrNull()

                if (template == null) {
                    return@suspendTransaction CouponClaimResult.Error("优惠券模板不存在")
                }

                // Check if template is active
                if (template[CouponTemplates.status] != 1) {
                    return@suspendTransaction CouponClaimResult.Error("优惠券已停用")
                }

                // Check if within valid period
                val now = LocalDateTime.now()
                if (now.isBefore(template[CouponTemplates.validFrom]) || now.isAfter(template[CouponTemplates.validTo])) {
                    return@suspendTransaction CouponClaimResult.Error("优惠券不在有效期内")
                }

                // Check if there are remaining coupons
                val issuedQuantity = template[CouponTemplates.issuedQuantity]
                val totalQuantity = template[CouponTemplates.totalQuantity]
                if (issuedQuantity >= totalQuantity) {
                    return@suspendTransaction CouponClaimResult.Error("优惠券已领完")
                }

                // Generate unique coupon code
                val couponCode = generateCouponCode(templateId, userId)

                // Create user coupon
                UserCoupons.insert {
                    it[UserCoupons.userId] = userId
                    it[UserCoupons.templateId] = templateId
                    it[UserCoupons.couponCode] = couponCode
                    it[UserCoupons.status] = "unused"
                }

                // Update issued quantity
                CouponTemplates.update({ CouponTemplates.id eq templateId }) {
                    it[CouponTemplates.issuedQuantity] = issuedQuantity + 1
                }

                CouponClaimResult.Success(
                    couponCode = couponCode,
                    couponName = template[CouponTemplates.name],
                    faceValue = template[CouponTemplates.faceValue].toDouble(),
                    validTo = template[CouponTemplates.validTo].format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                )
            }
        }
    }

    /**
     * Generate unique coupon code.
     */
    private fun generateCouponCode(templateId: Long, userId: Long): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (1000..9999).random()
        return "CPN${templateId}${timestamp}${random}"
    }
}

// DTOs

@Serializable
data class UserCouponDto(
    val id: Long,
    val couponCode: String,
    val templateName: String,
    val couponType: String,
    val faceValue: Double,
    val minConsumption: Double,
    val validFrom: String,
    val validTo: String,
    val status: String,
    val usedAt: String?
)

sealed class CouponRedeemResult {
    @Serializable
    data class Success(val couponName: String, val faceValue: Double) : CouponRedeemResult()
    @Serializable
    data class Error(val message: String) : CouponRedeemResult()
}

sealed class CouponClaimResult {
    @Serializable
    data class Success(
        val couponCode: String,
        val couponName: String,
        val faceValue: Double,
        val validTo: String
    ) : CouponClaimResult()
    @Serializable
    data class Error(val message: String) : CouponClaimResult()
}
