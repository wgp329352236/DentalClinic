package com.dentalclinic.common

import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.UserCoupons
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime

/**
 * 预约使用/释放优惠券的事务内操作。
 *
 * 本对象不自行开启事务，调用方必须已在 [BaseService.dbQuery] 内，并先锁住预约行，
 * 再通过本对象锁优惠券、锁模板，保证并发核销和取消释放使用一致的锁顺序。
 */
object CouponUsageSupport {

    sealed class UseResult {
        data class Success(
            val userCouponId: Long,
            val couponCode: String,
            val couponName: String,
            val faceValue: Double
        ) : UseResult()

        data class Error(val message: String) : UseResult()
    }

    /**
     * 将当前用户名下的优惠券原子核销到预约上，并同步模板已使用数量。
     *
     * @return 核销成功信息；失败时由调用方回滚整个事务并返回业务提示。
     */
    suspend fun useForAppointment(
        userId: Long,
        couponCode: String,
        appointmentId: Long,
        now: LocalDateTime = LocalDateTime.now()
    ): UseResult = use(userId, couponCode, appointmentId, now)

    /**
     * 独立核销接口（不绑定预约）使用。预约场景请优先调用 [useForAppointment]。
     */
    suspend fun use(
        userId: Long,
        couponCode: String,
        appointmentId: Long?,
        now: LocalDateTime = LocalDateTime.now()
    ): UseResult {
        val normalizedCode = couponCode.trim()
        if (normalizedCode.isEmpty()) return UseResult.Error("请选择有效优惠券")

        val coupon = UserCoupons.selectAll()
            .where {
                (UserCoupons.userId eq userId) and
                    (UserCoupons.couponCode eq normalizedCode)
            }
            .forUpdate()
            .toList()
            .singleOrNull()
            ?: return UseResult.Error("优惠券不存在")

        if (coupon[UserCoupons.status] != "unused") {
            return UseResult.Error(if (coupon[UserCoupons.status] == "used") "优惠券已使用" else "优惠券已过期")
        }

        val templateId = coupon[UserCoupons.templateId]
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq templateId }
            .forUpdate()
            .toList()
            .singleOrNull()
            ?: return UseResult.Error("优惠券模板不存在")

        if (template[CouponTemplates.status] != 1) {
            return UseResult.Error("优惠券已停用")
        }
        if (now.isBefore(template[CouponTemplates.validFrom])) {
            return UseResult.Error("优惠券尚未生效")
        }
        if (now.isAfter(template[CouponTemplates.validTo])) {
            return UseResult.Error("优惠券已过期")
        }

        UserCoupons.update({
            (UserCoupons.id eq coupon[UserCoupons.id]) and
                (UserCoupons.userId eq userId) and
                (UserCoupons.status eq "unused")
        }) {
            it[UserCoupons.status] = "used"
            it[UserCoupons.usedAt] = now
            it[UserCoupons.appointmentId] = appointmentId
        }

        CouponTemplates.update({ CouponTemplates.id eq templateId }) {
            it[CouponTemplates.usedQuantity] = template[CouponTemplates.usedQuantity] + 1
        }

        return UseResult.Success(
            userCouponId = coupon[UserCoupons.id],
            couponCode = normalizedCode,
            couponName = template[CouponTemplates.name],
            faceValue = template[CouponTemplates.faceValue].toDouble()
        )
    }

    /**
     * 释放预约占用的优惠券。仅释放仍由该预约占用且状态为 used 的券，避免误释放已转消费核销的券。
     *
     * @return 实际释放的券码，供日志或调用方提示使用。
     */
    suspend fun releaseForAppointment(
        userId: Long,
        appointmentId: Long
    ): List<String> {
        val coupons = UserCoupons.selectAll()
            .where {
                (UserCoupons.userId eq userId) and
                    (UserCoupons.appointmentId eq appointmentId) and
                    (UserCoupons.status eq "used")
            }
            .forUpdate()
            .toList()

        val released = mutableListOf<String>()
        for (coupon in coupons) {
            val templateId = coupon[UserCoupons.templateId]
            val template = CouponTemplates.selectAll()
                .where { CouponTemplates.id eq templateId }
                .forUpdate()
                .toList()
                .singleOrNull()

            UserCoupons.update({
                (UserCoupons.id eq coupon[UserCoupons.id]) and
                    (UserCoupons.appointmentId eq appointmentId) and
                    (UserCoupons.status eq "used")
            }) {
                it[UserCoupons.status] = "unused"
                it[UserCoupons.usedAt] = null
                it[UserCoupons.appointmentId] = null
            }

            if (template != null) {
                CouponTemplates.update({ CouponTemplates.id eq templateId }) {
                    it[CouponTemplates.usedQuantity] =
                        (template[CouponTemplates.usedQuantity] - 1).coerceAtLeast(0)
                }
            }
            released += coupon[UserCoupons.couponCode]
        }
        return released
    }
}
