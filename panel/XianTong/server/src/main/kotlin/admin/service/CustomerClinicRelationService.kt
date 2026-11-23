package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.CustomerClinicRelations
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 客户-门店业务关系写入。
 *
 * 客户不再有「归属门店」，门店维度数据由业务事实推导：预约、消费、充值等事件发生时，
 * 在业务事务内调用 [touch] 记录客户与该门店的关系。
 *
 * 注意：[touch] 是事务内方法（不自行开启事务），调用方必须在业务事务中调用，
 * 使关系记录与业务数据原子提交；事务回滚时关系也一并回滚。
 */
object CustomerClinicRelationService {

    /**
     * 记录一次客户与门店的业务往来。
     *
     * 关系不存在时插入（首次/最近到店均取 [at]）；已存在时把 [firstVisitAt] 取更早值、
     * [lastVisitAt] 取更晚值，并累加计数与消费金额。
     *
     * @param source 触发来源：appointment / consumption / recharge / manual 等
     * @param at 业务发生时间，默认当前时间
     * @param appointmentCount 本次新增预约数（通常 1）
     * @param consumptionCount 本次新增有效消费笔数（通常 1）
     * @param consumptionAmount 本次新增消费金额（与看板口径一致，取优惠后金额 amount）
     */
    suspend fun touch(
        userId: Long,
        clinicId: Long,
        source: String,
        at: LocalDateTime = LocalDateTime.now(),
        appointmentCount: Int = 0,
        consumptionCount: Int = 0,
        consumptionAmount: BigDecimal = BigDecimal.ZERO
    ) {
        val existing = CustomerClinicRelations.selectAll()
            .where {
                (CustomerClinicRelations.userId eq userId) and
                    (CustomerClinicRelations.clinicId eq clinicId)
            }
            .toList()
            .singleOrNull()

        if (existing == null) {
            CustomerClinicRelations.insert {
                it[CustomerClinicRelations.userId] = userId
                it[CustomerClinicRelations.clinicId] = clinicId
                it[CustomerClinicRelations.firstVisitAt] = at
                it[CustomerClinicRelations.lastVisitAt] = at
                it[CustomerClinicRelations.appointmentCount] = appointmentCount
                it[CustomerClinicRelations.consumptionCount] = consumptionCount
                it[CustomerClinicRelations.consumptionAmount] = consumptionAmount
                it[CustomerClinicRelations.visitSource] = source
                it[CustomerClinicRelations.updatedAt] = at
            }
        } else {
            CustomerClinicRelations.update({
                (CustomerClinicRelations.userId eq userId) and
                    (CustomerClinicRelations.clinicId eq clinicId)
            }) {
                if (at < existing[CustomerClinicRelations.firstVisitAt]) {
                    it[CustomerClinicRelations.firstVisitAt] = at
                }
                if (at > existing[CustomerClinicRelations.lastVisitAt]) {
                    it[CustomerClinicRelations.lastVisitAt] = at
                }
                it[CustomerClinicRelations.appointmentCount] =
                    existing[CustomerClinicRelations.appointmentCount] + appointmentCount
                it[CustomerClinicRelations.consumptionCount] =
                    existing[CustomerClinicRelations.consumptionCount] + consumptionCount
                it[CustomerClinicRelations.consumptionAmount] =
                    existing[CustomerClinicRelations.consumptionAmount] + consumptionAmount
                it[CustomerClinicRelations.updatedAt] = at
            }
        }
    }
}
