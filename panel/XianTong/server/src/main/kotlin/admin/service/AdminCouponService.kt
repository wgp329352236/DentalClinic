package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
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
            if (now.isBefore(template[CouponTemplates.validFrom]) ||
                now.isAfter(template[CouponTemplates.validTo])) {
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
     * 分页查询用户优惠券记录（支持按用户/模板/状态筛选）。
     */
    suspend fun getUserCoupons(
        userId: Long?,
        templateId: Long?,
        status: String?,
        page: Int,
        pageSize: Int
    ): UserCouponsResponse = dbQuery {
        var query = UserCoupons.selectAll()

        if (userId != null) {
            query = query.andWhere { UserCoupons.userId eq userId }
        }
        if (templateId != null) {
            query = query.andWhere { UserCoupons.templateId eq templateId }
        }
        if (!status.isNullOrBlank()) {
            query = query.andWhere { UserCoupons.status eq status }
        }

        val total = query.count()
        val rows = query
            .orderBy(UserCoupons.createdAt, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        // 批量加载模板名称，避免 N+1 查询
        val templateIds = rows.map { it[UserCoupons.templateId] }.distinct()
        val templateMap = if (templateIds.isNotEmpty()) {
            CouponTemplates.selectAll()
                .where { CouponTemplates.id inList templateIds }
                .toList()
                .associateBy { it[CouponTemplates.id] }
        } else emptyMap()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val list = rows.map { row ->
            val template = templateMap[row[UserCoupons.templateId]]
            UserCouponAdminDto(
                id = row[UserCoupons.id],
                userId = row[UserCoupons.userId],
                templateId = row[UserCoupons.templateId],
                couponName = template?.get(CouponTemplates.name) ?: "未知优惠券",
                couponCode = row[UserCoupons.couponCode],
                // 券属性一并返回：录入消费时需要据此判断能否抵扣（仅现金券、需达门槛、需在有效期内）
                couponType = template?.get(CouponTemplates.couponType),
                faceValue = template?.get(CouponTemplates.faceValue)?.toDouble(),
                minConsumption = template?.get(CouponTemplates.minConsumption)?.toDouble(),
                validTo = template?.get(CouponTemplates.validTo)?.format(formatter),
                status = row[UserCoupons.status],
                usedAt = row[UserCoupons.usedAt]?.format(formatter),
                createdAt = row[UserCoupons.createdAt].format(formatter)
            )
        }

        UserCouponsResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    /**
     * 删除优惠券模板。已发放过的模板禁止物理删除，避免破坏用户券的引用完整性。
     */
    suspend fun deleteCouponTemplate(id: Long): DeleteCouponResult = dbQuery {
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq id }
            .toList()
            .singleOrNull()
            ?: return@dbQuery DeleteCouponResult.Error("优惠券模板不存在")

        val issued = template[CouponTemplates.issuedQuantity]
        if (issued > 0) {
            return@dbQuery DeleteCouponResult.Error("该模板已发放 $issued 张，无法删除，建议改为停用")
        }

        CouponTemplates.deleteWhere { CouponTemplates.id eq id }
        DeleteCouponResult.Success
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

@Serializable
data class UserCouponAdminDto(
    val id: Long,
    val userId: Long,
    val templateId: Long,
    val couponName: String,
    val couponCode: String,
    /** discount/cash/service，消费录入仅支持 cash 抵扣 */
    val couponType: String? = null,
    val faceValue: Double? = null,
    val minConsumption: Double? = null,
    val validTo: String? = null,
    val status: String,
    val usedAt: String?,
    val createdAt: String
)

@Serializable
data class UserCouponsResponse(
    val list: List<UserCouponAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

sealed class DeleteCouponResult {
    @Serializable
    object Success : DeleteCouponResult()
    @Serializable
    data class Error(val message: String) : DeleteCouponResult()
}
