package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.config.DBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Admin coupon management service.
 */
class AdminCouponService {

    /**
     * Get all coupon templates.
     */
    suspend fun getCouponTemplates(): List<CouponTemplateDto> = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Create a new coupon template.
     */
    suspend fun createCouponTemplate(request: CreateCouponTemplateRequest): Long = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Update a coupon template.
     */
    suspend fun updateCouponTemplate(id: Long, request: UpdateCouponTemplateRequest): Unit = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
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
    }

    /**
     * Grant coupons to specific users manually.
     */
    suspend fun grantCouponsToUsers(templateId: Long, userIds: List<Long>, operator: String): GrantResult {
        return withContext(Dispatchers.IO) {
            suspendTransaction(DBConfig.database) {
                // Check if template exists
                val template = CouponTemplates.selectAll()
                    .where { CouponTemplates.id eq templateId }
                    .toList()
                    .singleOrNull()

                if (template == null) {
                    return@suspendTransaction GrantResult.Error("优惠券模板不存在")
                }

                // Check stock
                val issuedQuantity = template[CouponTemplates.issuedQuantity]
                val totalQuantity = template[CouponTemplates.totalQuantity]
                val remaining = totalQuantity - issuedQuantity

                if (remaining < userIds.size) {
                    return@suspendTransaction GrantResult.Error("库存不足，剩余：$remaining，需要：${userIds.size}")
                }

                var successCount = 0
                val failedUsers = mutableListOf<Long>()

                for (userId in userIds) {
                    try {
                        // Generate unique coupon code
                        val couponCode = generateCouponCode(templateId, userId)

                        // Create user coupon
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
        }
    }

    /**
     * Get coupon usage statistics.
     */
    suspend fun getCouponStatistics(templateId: Long? = null): CouponStatisticsDto = withContext(Dispatchers.IO) {
        suspendTransaction(DBConfig.database) {
            val templates = if (templateId != null) {
                CouponTemplates.selectAll().where { CouponTemplates.id eq templateId }.toList()
            } else {
                CouponTemplates.selectAll().toList()
            }

            var totalTemplates = templates.size
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
                totalTemplates = totalTemplates,
                totalIssued = totalIssued,
                totalUsed = totalUsed,
                totalRemaining = totalRemaining,
                usageRate = String.format("%.2f", usageRate).toDouble()
            )
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
