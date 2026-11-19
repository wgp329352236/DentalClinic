package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.PointsExchangeOrders
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 管理端积分商城服务（P1-9：核销台）。
 */
class AdminPointsMallService : BaseService() {

    /**
     * 核销积分兑换订单。
     *
     * @param redeemCode 兑换码
     * @param operatorName 核销操作员姓名
     * @return 核销结果
     */
    suspend fun redeemOrder(redeemCode: String, operatorName: String): RedeemResult = dbQuery {
        val order = PointsExchangeOrders.selectAll()
            .where { PointsExchangeOrders.redeemCode eq redeemCode }
            .toList()
            .firstOrNull()
            ?: throw NotFoundException("兑换码不存在")

        if (order[PointsExchangeOrders.status] == "used") {
            throw ValidationException("该兑换码已使用")
        }

        val now = LocalDateTime.now()
        PointsExchangeOrders.update({ PointsExchangeOrders.id eq order[PointsExchangeOrders.id] }) {
            it[PointsExchangeOrders.status] = "used"
            it[PointsExchangeOrders.redeemedAt] = now
            it[PointsExchangeOrders.redeemedBy] = operatorName
        }

        RedeemResult(
            orderId = order[PointsExchangeOrders.id],
            userId = order[PointsExchangeOrders.userId],
            goodsName = order[PointsExchangeOrders.goodsName],
            pointsCost = order[PointsExchangeOrders.pointsCost],
            redeemedAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }

    /**
     * 查询待核销的兑换订单列表。
     */
    suspend fun listPendingOrders(page: Int, pageSize: Int): PendingOrdersResponse = dbQuery {
        val total = PointsExchangeOrders.selectAll()
            .where { PointsExchangeOrders.status eq "unused" }
            .count()

        val orders = PointsExchangeOrders.selectAll()
            .where { PointsExchangeOrders.status eq "unused" }
            .orderBy(PointsExchangeOrders.createdAt, SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
            .map { order ->
                PendingOrderDto(
                    id = order[PointsExchangeOrders.id],
                    userId = order[PointsExchangeOrders.userId],
                    goodsName = order[PointsExchangeOrders.goodsName],
                    pointsCost = order[PointsExchangeOrders.pointsCost],
                    redeemCode = order[PointsExchangeOrders.redeemCode],
                    createdAt = order[PointsExchangeOrders.createdAt].format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    )
                )
            }

        PendingOrdersResponse(
            list = orders,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }
}

@Serializable
data class RedeemResult(
    val orderId: Long,
    val userId: Long,
    val goodsName: String,
    val pointsCost: Int,
    val redeemedAt: String
)

@Serializable
data class PendingOrderDto(
    val id: Long,
    val userId: Long,
    val goodsName: String,
    val pointsCost: Int,
    val redeemCode: String,
    val createdAt: String
)

@Serializable
data class PendingOrdersResponse(
    val list: List<PendingOrderDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)
