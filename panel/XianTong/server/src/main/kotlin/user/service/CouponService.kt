package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.CouponUsageSupport
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Coupon service for user voucher management.
 */
class CouponService : BaseService() {

    /**
     * Get user's coupon list.
     */
    suspend fun getUserCoupons(userId: Long, status: String?): List<UserCouponDto> = dbQuery {
        val coupons = UserCoupons.selectAll()
            .where { UserCoupons.userId eq userId }
            .orderBy(UserCoupons.createdAt, SortOrder.DESC)
            .toList()

        val templateIds = coupons.map { it[UserCoupons.templateId] }.distinct()
        val templates = if (templateIds.isEmpty()) emptyMap() else {
            CouponTemplates.selectAll()
                .where { CouponTemplates.id inList templateIds }
                .toList()
                .associateBy { it[CouponTemplates.id] }
        }
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        coupons.mapNotNull { couponRow ->
            val templateId = couponRow[UserCoupons.templateId]
            val template = templates[templateId] ?: return@mapNotNull null
            val effectiveStatus = effectiveStatus(couponRow[UserCoupons.status], template[CouponTemplates.validTo], now)
            if (!status.isNullOrEmpty() && effectiveStatus != status) {
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
                status = effectiveStatus,
                usedAt = couponRow[UserCoupons.usedAt]?.format(formatter)
            )
        }
    }

    /**
     * Redeem (use) a coupon.
     */
    suspend fun redeemCoupon(userId: Long, couponCode: String, appointmentId: Long?): CouponRedeemResult {
        return dbQuery {
            if (appointmentId != null) {
                Appointments.selectAll()
                    .where {
                        (Appointments.id eq appointmentId) and
                            (Appointments.userId eq userId) and
                            (Appointments.status neq "cancelled")
                    }
                    .forUpdate()
                    .toList()
                    .singleOrNull()
                    ?: return@dbQuery CouponRedeemResult.Error("预约不存在或已取消")
            }

            when (val result = CouponUsageSupport.use(userId, couponCode, appointmentId)) {
                is CouponUsageSupport.UseResult.Error -> CouponRedeemResult.Error(result.message)
                is CouponUsageSupport.UseResult.Success -> CouponRedeemResult.Success(
                    couponName = result.couponName,
                    faceValue = result.faceValue
                )
            }
        }
    }

    /**
     * Claim a coupon by template ID.
     */
    suspend fun claimCoupon(userId: Long, templateId: Long): CouponClaimResult = dbQuery {
        // Check if template exists and is available
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq templateId }
            .toList()
            .singleOrNull()

        if (template == null) {
            return@dbQuery CouponClaimResult.Error("优惠券模板不存在")
        }

        // Check if template is active
        if (template[CouponTemplates.status] != 1) {
            return@dbQuery CouponClaimResult.Error("优惠券已停用")
        }

        // Check if within valid period
        val now = LocalDateTime.now()
        if (now.isBefore(template[CouponTemplates.validFrom]) || now.isAfter(template[CouponTemplates.validTo])) {
            return@dbQuery CouponClaimResult.Error("优惠券不在有效期内")
        }

        // Check if there are remaining coupons
        val issuedQuantity = template[CouponTemplates.issuedQuantity]
        val totalQuantity = template[CouponTemplates.totalQuantity]
        if (issuedQuantity >= totalQuantity) {
            return@dbQuery CouponClaimResult.Error("优惠券已领完")
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

    /**
     * 推广领券（扫码进入小程序自动领取，需求：领 2 张）。
     *
     * 与 [claimCoupon] 的差异：
     * ① 幂等：该用户只要领过该模板任意一张（含此前普通领取），直接拒绝，
     *    防止扫码反复刷券；判定依据是 user_coupon 中 userId+templateId 是否有记录。
     * ② 一次发 2 张：循环 [claimCoupon] 两次，两张券码互不相同。
     *
     * 校验（模板存在/启用/有效期/库存）复用 claimCoupon 内部逻辑，
     * 任一失败即整体失败，不产生部分发放。
     */
    suspend fun claimPromo(userId: Long, templateId: Long): PromoClaimResult = dbQuery {
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq templateId }
            .toList()
            .singleOrNull()
        if (template == null) {
            return@dbQuery PromoClaimResult.Error("优惠券模板不存在")
        }

        // 幂等：该用户该模板已领过则不再发放
        val alreadyClaimed = UserCoupons.selectAll()
            .where { (UserCoupons.userId eq userId) and (UserCoupons.templateId eq templateId) }
            .count() > 0
        if (alreadyClaimed) {
            return@dbQuery PromoClaimResult.Error("您已领取过该推广优惠券")
        }

        // 发 2 张：任一失败则整体失败（模板校验在 claimCoupon 内完成）
        val first = when (val r1 = claimCoupon(userId, templateId)) {
            is CouponClaimResult.Error -> return@dbQuery PromoClaimResult.Error(r1.message)
            is CouponClaimResult.Success -> r1
        }
        val second = when (val r2 = claimCoupon(userId, templateId)) {
            is CouponClaimResult.Error -> return@dbQuery PromoClaimResult.Error(r2.message)
            is CouponClaimResult.Success -> r2
        }

        PromoClaimResult.Success(
            coupons = listOf(
                PromoClaimedCoupon(
                    couponCode = first.couponCode,
                    couponName = first.couponName,
                    faceValue = first.faceValue,
                    validTo = first.validTo
                ),
                PromoClaimedCoupon(
                    couponCode = second.couponCode,
                    couponName = second.couponName,
                    faceValue = second.faceValue,
                    validTo = second.validTo
                )
            )
        )
    }

    /**
     * Generate unique coupon code.
     */
    private fun generateCouponCode(templateId: Long, userId: Long): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (1000..9999).random()
        return "CPN${templateId}${timestamp}${random}"
    }

    /**
     * Get coupon detail by ID for QR code generation.
     */
    suspend fun getCouponDetail(userId: Long, couponId: Long): CouponDetailDto? = dbQuery {
        val coupon = UserCoupons.selectAll()
            .where {
                (UserCoupons.id eq couponId) and
                (UserCoupons.userId eq userId)
            }
            .toList()
            .singleOrNull() ?: return@dbQuery null

        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq coupon[UserCoupons.templateId] }
            .toList()
            .singleOrNull() ?: return@dbQuery null

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val effectiveStatus = effectiveStatus(
            coupon[UserCoupons.status],
            template[CouponTemplates.validTo],
            LocalDateTime.now()
        )

        CouponDetailDto(
            id = coupon[UserCoupons.id],
            couponCode = coupon[UserCoupons.couponCode],
            templateName = template[CouponTemplates.name],
            couponType = template[CouponTemplates.couponType],
            faceValue = template[CouponTemplates.faceValue].toDouble(),
            minConsumption = template[CouponTemplates.minConsumption].toDouble(),
            validFrom = template[CouponTemplates.validFrom].format(formatter),
            validTo = template[CouponTemplates.validTo].format(formatter),
            status = effectiveStatus,
            qrData = "${coupon[UserCoupons.couponCode]}|${userId}|${coupon[UserCoupons.id]}"
        )
    }

    private fun effectiveStatus(status: String, validTo: LocalDateTime, now: LocalDateTime): String =
        if (status == "unused" && now.isAfter(validTo)) "expired" else status
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

/** 推广领券结果（一次领 2 张）。 */
sealed class PromoClaimResult {
    @Serializable
    data class Success(val coupons: List<PromoClaimedCoupon>) : PromoClaimResult()
    @Serializable
    data class Error(val message: String) : PromoClaimResult()
}

@Serializable
data class PromoClaimedCoupon(
    val couponCode: String,
    val couponName: String,
    val faceValue: Double,
    val validTo: String
)

@Serializable
data class CouponDetailDto(
    val id: Long,
    val couponCode: String,
    val templateName: String,
    val couponType: String,
    val faceValue: Double,
    val minConsumption: Double,
    val validFrom: String,
    val validTo: String,
    val status: String,
    val qrData: String  // QR code data: couponCode|userId|couponId
)
