package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.PointsExchangeOrders
import com.dentalclinic.admin.entity.PointsGoods
import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 积分商城服务（需求 3.4.2：500 积分兑换口腔检查、2000 积分兑换电动牙刷）。
 *
 * 兑换为单向操作：扣积分 + 生成唯一核销码，用户到店由前台核销（P2 做管理端核销台）。
 *
 * 并发安全：
 * - 库存：条件更新 `WHERE total_stock=-1 OR exchanged_count<total_stock`，
 *   flipped 行数为 0 即抢光，抛 [MallRollbackException] 回滚整事务（订单与扣分一并撤销）。
 * - 积分：条件更新 `WHERE total_balance>=price`，同样靠 flipped 行数兜底超扣。
 * - 核销码：插入前查重重试；表上 redeem_code 唯一索引是最后防线。
 */
class PointsMallService : BaseService() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 上架商品列表（C 端只看得到 status=1）。 */
    suspend fun listGoods(): List<PointsGoodsDto> = dbQuery {
        PointsGoods.selectAll()
            .where { PointsGoods.status eq 1 }
            .orderBy(PointsGoods.sortOrder, SortOrder.ASC)
            .orderBy(PointsGoods.id, SortOrder.ASC)
            .toList()
            .map { row ->
                val total = row[PointsGoods.totalStock]
                val exchanged = row[PointsGoods.exchangedCount]
                PointsGoodsDto(
                    id = row[PointsGoods.id],
                    name = row[PointsGoods.name],
                    goodsType = row[PointsGoods.goodsType],
                    pointsPrice = row[PointsGoods.pointsPrice],
                    coverImage = row[PointsGoods.coverImage],
                    description = row[PointsGoods.description],
                    // -1=不限量；否则返回剩余可兑换数（不为负，防御历史脏数据）
                    remainingStock = if (total < 0) -1 else (total - exchanged).coerceAtLeast(0),
                    sortOrder = row[PointsGoods.sortOrder]
                )
            }
    }

    /**
     * 兑换商品。成功返回含核销码的订单；业务失败（下架/售罄/积分不足）返回 [PointsMallResult.Error]。
     */
    suspend fun exchange(userId: Long, goodsId: Long): PointsMallResult =
        try {
            dbQuery {
                val goods = PointsGoods.selectAll()
                    .where { PointsGoods.id eq goodsId }
                    .toList()
                    .singleOrNull()
                    ?: throw MallRollbackException("商品不存在")

                if (goods[PointsGoods.status] != 1) {
                    throw MallRollbackException("商品已下架")
                }

                val price = goods[PointsGoods.pointsPrice]
                if (price <= 0) throw MallRollbackException("商品配置异常")

                val balanceBefore = UserPointsSummary.selectAll()
                    .where { UserPointsSummary.userId eq userId }
                    .toList()
                    .singleOrNull()
                    ?.get(UserPointsSummary.totalBalance) ?: 0

                if (balanceBefore < price) {
                    throw MallRollbackException("积分余额不足")
                }

                val goodsName = goods[PointsGoods.name]
                val redeemCode = generateUniqueRedeemCode()

                // 先落订单（积分流水以订单 id 为 sourceId 溯源）；后续任一步失败整事务回滚，订单不会残留
                val orderId = PointsExchangeOrders.insert {
                    it[PointsExchangeOrders.userId] = userId
                    it[PointsExchangeOrders.goodsId] = goodsId
                    it[PointsExchangeOrders.goodsName] = goodsName
                    it[PointsExchangeOrders.pointsCost] = price
                    it[PointsExchangeOrders.redeemCode] = redeemCode
                    it[PointsExchangeOrders.status] = "unused"
                } get PointsExchangeOrders.id

                // 条件扣分：并发兑换时余额可能已被其他事务消耗，flipped=0 即余额不足
                val deducted = UserPointsSummary.update({
                    (UserPointsSummary.userId eq userId) and
                    (UserPointsSummary.totalBalance greaterEq price)
                }) {
                    it[UserPointsSummary.totalBalance] = UserPointsSummary.totalBalance - price
                }
                if (deducted == 0) throw MallRollbackException("积分余额不足")

                PointsRecords.insert {
                    it[PointsRecords.userId] = userId
                    it[PointsRecords.changeAmount] = -price
                    it[PointsRecords.balanceAfter] = balanceBefore - price
                    it[PointsRecords.sourceType] = "points_exchange"
                    it[PointsRecords.sourceId] = orderId
                    it[PointsRecords.description] = "积分兑换：$goodsName"
                    // 消耗行不参与批次过期计算（扫描只看 changeAmount>0），expiresAt 仅为 NOT NULL 占位
                    it[PointsRecords.expiresAt] = LocalDateTime.now().plusMonths(12)
                }

                // 条件占库存：不限量(-1) 或仍有剩余时 flipped=1
                val stockTaken = PointsGoods.update({
                    (PointsGoods.id eq goodsId) and
                    (PointsGoods.status eq 1) and
                    ((PointsGoods.totalStock eq -1) or
                        (PointsGoods.exchangedCount less PointsGoods.totalStock))
                }) {
                    it[PointsGoods.exchangedCount] = PointsGoods.exchangedCount + 1
                }
                if (stockTaken == 0) throw MallRollbackException("商品已兑罄")

                PointsMallResult.Success(
                    ExchangeOrderDto(
                        id = orderId,
                        goodsId = goodsId,
                        goodsName = goodsName,
                        pointsCost = price,
                        redeemCode = redeemCode,
                        status = "unused",
                        redeemedAt = null,
                        createdAt = LocalDateTime.now().format(dateTimeFormatter)
                    )
                )
            }
        } catch (e: MallRollbackException) {
            PointsMallResult.Error(e.message ?: "兑换失败")
        }

    /** 我的兑换订单（含已核销），最新在前。 */
    suspend fun getMyOrders(userId: Long): List<ExchangeOrderDto> = dbQuery {
        PointsExchangeOrders.selectAll()
            .where { PointsExchangeOrders.userId eq userId }
            .orderBy(PointsExchangeOrders.createdAt, SortOrder.DESC)
            .toList()
            .map { row ->
                ExchangeOrderDto(
                    id = row[PointsExchangeOrders.id],
                    goodsId = row[PointsExchangeOrders.goodsId],
                    goodsName = row[PointsExchangeOrders.goodsName],
                    pointsCost = row[PointsExchangeOrders.pointsCost],
                    redeemCode = row[PointsExchangeOrders.redeemCode],
                    status = row[PointsExchangeOrders.status],
                    redeemedAt = row[PointsExchangeOrders.redeemedAt]?.format(dateTimeFormatter),
                    createdAt = row[PointsExchangeOrders.createdAt].format(dateTimeFormatter)
                )
            }
    }

    /** 生成不冲突的核销码：PE + 时间戳后 6 位 + 4 位随机数，查重最多 10 次。 */
    private suspend fun generateUniqueRedeemCode(): String {
        repeat(10) {
            val code = "PE${System.currentTimeMillis().toString().takeLast(6)}" +
                Random.nextInt(0, 10000).toString().padStart(4, '0')
            val exists = PointsExchangeOrders.selectAll()
                .where { PointsExchangeOrders.redeemCode eq code }
                .count() > 0
            if (!exists) return code
        }
        // 理论上不会到这里；用纳秒随机兜底，唯一索引仍是最后防线
        return "PE${System.currentTimeMillis()}${Random.nextInt(100000, 999999)}"
    }

    /** 触发整事务回滚的业务异常，仅在本类内使用。 */
    private class MallRollbackException(message: String) : RuntimeException(message)
}

// DTOs

@Serializable
data class PointsGoodsDto(
    val id: Long,
    val name: String,
    val goodsType: String,
    val pointsPrice: Int,
    val coverImage: String?,
    val description: String?,
    val remainingStock: Int,
    val sortOrder: Int
)

@Serializable
data class ExchangeOrderDto(
    val id: Long,
    val goodsId: Long,
    val goodsName: String,
    val pointsCost: Int,
    val redeemCode: String,
    val status: String,
    val redeemedAt: String?,
    val createdAt: String
)

sealed class PointsMallResult {
    @Serializable
    data class Success(val order: ExchangeOrderDto) : PointsMallResult()
    @Serializable
    data class Error(val message: String) : PointsMallResult()
}
