package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.admin.entity.Users
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Admin coupon management service.
 */
class AdminCouponService : BaseService() {

    /**
     * Get all coupon templates.
     */
    suspend fun getCouponTemplates(): List<CouponTemplateDto> = dbQuery {
        CouponTemplates.selectAll()
            .orderBy(CouponTemplates.createdAt, SortOrder.DESC)
            .toList()
            .map { row ->
                CouponTemplateDto(
                    id = row[CouponTemplates.id],
                    name = row[CouponTemplates.name],
                    couponType = row[CouponTemplates.couponType],
                    faceValue = row[CouponTemplates.faceValue].toDouble(),
                    minConsumption = row[CouponTemplates.minConsumption].toDouble(),
                    applicableDepartments = row[CouponTemplates.applicableDepartments],
                    validFrom = row[CouponTemplates.validFrom].format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    validTo = row[CouponTemplates.validTo].format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    totalQuantity = row[CouponTemplates.totalQuantity],
                    issuedQuantity = row[CouponTemplates.issuedQuantity],
                    usedQuantity = row[CouponTemplates.usedQuantity],
                    status = row[CouponTemplates.status]
                )
            }
    }

    /**
     * Create a new coupon template.
     */
    suspend fun createCouponTemplate(request: CreateCouponTemplateRequest): Long = dbQuery {
        CouponTemplates.insert {
            it[name] = request.name
            it[couponType] = request.couponType
            it[faceValue] = request.faceValue.toBigDecimal()
            it[minConsumption] = request.minConsumption.toBigDecimal()
            it[applicableDepartments] = request.applicableDepartments
            it[validFrom] = LocalDateTime.parse(request.validFrom + "T00:00:00")
            it[validTo] = LocalDateTime.parse(request.validTo + "T23:59:59")
            it[totalQuantity] = request.totalQuantity
            it[issuedQuantity] = 0
            it[usedQuantity] = 0
            it[status] = request.status
        } get CouponTemplates.id
    }

    /**
     * Update a coupon template.
     */
    suspend fun updateCouponTemplate(id: Long, request: UpdateCouponTemplateRequest): Unit = dbQuery {
        CouponTemplates.update({ CouponTemplates.id eq id }) {
            it[name] = request.name
            it[couponType] = request.couponType
            it[faceValue] = request.faceValue.toBigDecimal()
            it[minConsumption] = request.minConsumption.toBigDecimal()
            it[applicableDepartments] = request.applicableDepartments
            it[validFrom] = LocalDateTime.parse(request.validFrom + "T00:00:00")
            it[validTo] = LocalDateTime.parse(request.validTo + "T23:59:59")
            it[totalQuantity] = request.totalQuantity
            it[status] = request.status
        }
    }

    /**
     * Grant coupons to specific users manually.
     */
    suspend fun grantCouponsToUsers(templateId: Long, userIds: List<Long>, operator: String): GrantResult = dbQuery {
        // Check if template exists
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq templateId }
            .toList()
            .singleOrNull()
            ?: return@dbQuery GrantResult.Error("优惠券模板不存在")

        // Check stock
        val issuedQuantity = template[CouponTemplates.issuedQuantity]
        val totalQuantity = template[CouponTemplates.totalQuantity]
        val remaining = totalQuantity - issuedQuantity

        if (remaining < userIds.size) {
            return@dbQuery GrantResult.Error("库存不足，剩余：$remaining，需要：${userIds.size}")
        }

        var successCount = 0
        val failedUsers = mutableListOf<Long>()

        for (userId in userIds) {
            try {
                val couponCode = generateCouponCode(templateId, userId)
                UserCoupons.insert {
                    it[UserCoupons.userId] = userId
                    it[UserCoupons.templateId] = templateId
                    it[UserCoupons.couponCode] = couponCode
                    it[UserCoupons.status] = "unused"
                }
                successCount++
            } catch (e: Exception) {
                failedUsers.add(userId)
            }
        }

        // Update issued quantity
        CouponTemplates.update({ CouponTemplates.id eq templateId }) {
            it[CouponTemplates.issuedQuantity] = issuedQuantity + successCount
        }

        if (failedUsers.isEmpty()) {
            GrantResult.Success(successCount)
        } else {
            GrantResult.PartialSuccess(successCount, failedUsers)
        }
    }

    /**
     * Get coupon usage statistics.
     */
    suspend fun getCouponStatistics(templateId: Long? = null): CouponStatisticsDto = dbQuery {
        val templates = if (templateId != null) {
            CouponTemplates.selectAll().where { CouponTemplates.id eq templateId }.toList()
        } else {
            CouponTemplates.selectAll().toList()
        }

        var totalIssued = 0
        var totalUsed = 0
        var totalRemaining = 0

        for (template in templates) {
            val issued = template[CouponTemplates.issuedQuantity]
            val used = template[CouponTemplates.usedQuantity]
            val total = template[CouponTemplates.totalQuantity]

            totalIssued += issued
            totalUsed += used
            totalRemaining += (total - issued)
        }

        val usageRate = if (totalIssued > 0) {
            (totalUsed.toDouble() / totalIssued.toDouble()) * 100
        } else 0.0

        CouponStatisticsDto(
            totalTemplates = templates.size,
            totalIssued = totalIssued,
            totalUsed = totalUsed,
            totalRemaining = totalRemaining,
            usageRate = String.format("%.2f", usageRate).toDouble()
        )
    }

    /**
     * Scan and redeem coupon by QR data.
     */
    suspend fun scanAndRedeemCoupon(qrData: String, operator: String): ScanRedeemResult = dbQuery {
        try {
            // Parse QR data: couponCode|userId|couponId
            val parts = qrData.split("|")
            if (parts.size != 3) {
                return@dbQuery ScanRedeemResult.Error("无效的二维码格式")
            }

            val couponCode = parts[0]
            val userId = parts[1].toLongOrNull() ?: return@dbQuery ScanRedeemResult.Error("无效的用户ID")
            val couponId = parts[2].toLongOrNull() ?: return@dbQuery ScanRedeemResult.Error("无效的优惠券ID")

            // Find the coupon
            val coupon = UserCoupons.selectAll()
                .where {
                    (UserCoupons.id eq couponId) and
                    (UserCoupons.userId eq userId) and
                    (UserCoupons.couponCode eq couponCode)
                }
                .toList()
                .singleOrNull()
                ?: return@dbQuery ScanRedeemResult.Error("优惠券不存在或已被使用")

            // Check status
            if (coupon[UserCoupons.status] != "unused") {
                return@dbQuery ScanRedeemResult.Error("优惠券已使用或已过期")
            }

            // Get template and check validity
            val template = CouponTemplates.selectAll()
                .where { CouponTemplates.id eq coupon[UserCoupons.templateId] }
                .toList()
                .singleOrNull()
                ?: return@dbQuery ScanRedeemResult.Error("优惠券模板不存在")

            val now = LocalDateTime.now()
            if (now.isBefore(template[CouponTemplates.validFrom]) || now.isAfter(template[CouponTemplates.validTo])) {
                return@dbQuery ScanRedeemResult.Error("优惠券已过期")
            }

            // Mark as used
            UserCoupons.update({ UserCoupons.id eq couponId }) {
                it[UserCoupons.status] = "used"
                it[UserCoupons.usedAt] = now
            }

            // Update used quantity in template
            CouponTemplates.update({ CouponTemplates.id eq coupon[UserCoupons.templateId] }) {
                it[CouponTemplates.usedQuantity] = template[CouponTemplates.usedQuantity] + 1
            }

            ScanRedeemResult.Success(
                couponName = template[CouponTemplates.name],
                faceValue = template[CouponTemplates.faceValue].toDouble()
            )
        } catch (e: Exception) {
            ScanRedeemResult.Error("核销失败: ${e.message}")
        }
    }

    /**
     * Generate unique coupon code.
     */
    private fun generateCouponCode(templateId: Long, userId: Long): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (1000..9999).random()
        return "ADM${templateId}${timestamp}${random}"
    }
}

// DTOs

@Serializable
data class CouponTemplateDto(
    val id: Long,
    val name: String,
    val couponType: String,
    val faceValue: Double,
    val minConsumption: Double,
    val applicableDepartments: String?,
    val validFrom: String,
    val validTo: String,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val usedQuantity: Int,
    val status: Int
)

@Serializable
data class CreateCouponTemplateRequest(
    val name: String,
    val couponType: String,
    val faceValue: Double,
    val minConsumption: Double = 0.0,
    val applicableDepartments: String? = null,
    val validFrom: String,
    val validTo: String,
    val totalQuantity: Int,
    val status: Int = 1
)

@Serializable
data class UpdateCouponTemplateRequest(
    val name: String,
    val couponType: String,
    val faceValue: Double,
    val minConsumption: Double = 0.0,
    val applicableDepartments: String? = null,
    val validFrom: String,
    val validTo: String,
    val totalQuantity: Int,
    val status: Int = 1
)

@Serializable
data class GrantCouponsRequest(
    val templateId: Long,
    val userIds: List<Long>
)

sealed class GrantResult {
    @Serializable
    data class Success(val count: Int) : GrantResult()
    @Serializable
    data class PartialSuccess(val successCount: Int, val failedUsers: List<Long>) : GrantResult()
    @Serializable
    data class Error(val message: String) : GrantResult()
}

@Serializable
data class CouponStatisticsDto(
    val totalTemplates: Int,
    val totalIssued: Int,
    val totalUsed: Int,
    val totalRemaining: Int,
    val usageRate: Double
)

@Serializable
data class ScanRedeemRequest(
    val qrData: String  // Format: couponCode|userId|couponId
)

sealed class ScanRedeemResult {
    @Serializable
    data class Success(val couponName: String, val faceValue: Double) : ScanRedeemResult()
    @Serializable
    data class Error(val message: String) : ScanRedeemResult()
}
