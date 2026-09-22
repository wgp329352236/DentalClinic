package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.Appointments
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.ConsumptionRecords
import com.dentalclinic.admin.entity.CouponTemplates
import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.PointsRecords
import com.dentalclinic.admin.entity.PointsRules
import com.dentalclinic.admin.entity.UserCoupons
import com.dentalclinic.admin.entity.UserMemberLevels
import com.dentalclinic.admin.entity.UserPointsSummary
import com.dentalclinic.admin.entity.UserWallets
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.ReferralRewardOutcome
import com.dentalclinic.common.ReferralRewardService
import com.dentalclinic.common.scheduler.ScheduledNotifier
import com.dentalclinic.common.config.PointsDeductConfig
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 消费录入与结算服务（管理端）。
 *
 * 打通需求中的消费主链路：会员折扣 → 优惠券抵扣 → 余额扣减 → 消费积分 →
 * 累计消费与等级升级 → 推荐奖励 → 站内消息。整条链路在单一事务内完成，
 * 任一步失败整体回滚，不会出现「扣了余额却没写消费记录」的中间态。
 *
 * 金额口径见 [ConsumptionRecords] 的表注释。
 */
class ConsumptionAdminService(
    private val pointsService: AdminPointsService,
    private val referralRewardService: ReferralRewardService
) : BaseService() {

    private val logger = LoggerFactory.getLogger("ConsumptionAdminService")

    /**
     * 录入一笔消费并完成结算。
     *
     * @param operator 操作员账号，写入消费记录用于审计
     */
    suspend fun createConsumption(
        request: CreateConsumptionRequest,
        operator: String
    ): ConsumptionSettlementDto = dbQuery {
        val now = LocalDateTime.now()

        if (request.originalAmount <= 0) {
            throw ValidationException("消费金额必须大于0")
        }

        val user = Users.selectAll()
            .where { Users.id eq request.userId }
            .toList()
            .firstOrNull()
            ?: throw NotFoundException("客户不存在")
        if (user[Users.status] == 0) {
            throw ValidationException("客户已被禁用，无法录入消费")
        }

        val clinic = Clinics.selectAll()
            .where { Clinics.id eq request.clinicId }
            .toList()
            .firstOrNull()
            ?: throw NotFoundException("门店不存在")
        if (clinic[Clinics.status] == 0) {
            throw ValidationException("门店「${clinic[Clinics.name]}」已停业，无法录入消费")
        }

        val appointment = request.appointmentId?.let { appointmentId ->
            Appointments.selectAll()
                .where { Appointments.id eq appointmentId }
                .toList()
                .firstOrNull()
                ?: throw NotFoundException("关联的预约记录不存在")
        }
        if (appointment != null && appointment[Appointments.userId] != request.userId) {
            throw ValidationException("关联的预约记录不属于该客户")
        }

        // 1. 会员等级折扣（需求 3.3.2）
        val levelSnapshot = loadMemberLevel(request.userId)
        val originalAmount = request.originalAmount.toBigDecimal().money()
        val discountAmount = originalAmount.subtract(originalAmount.multiply(levelSnapshot.discountRate).money()).money()
        val discountedAmount = originalAmount.subtract(discountAmount)

        // 2. 优惠券抵扣（仅校验并锁定，实际核销在消费记录落库之后）
        val coupon = request.userCouponId?.let {
            resolveCoupon(it, request.userId, discountedAmount, appointment)
        }
        val couponDeduct = coupon?.deduct ?: BigDecimal.ZERO
        val payableBeforePoints = discountedAmount.subtract(couponDeduct).money()

        // 2.5 积分抵扣诊疗费（需求 3.4.2：100 积分抵 1 元，单笔最高抵扣应付金额的一定比例）
        val pointsDeduction = resolvePointsDeduction(request.userId, request.usePoints, payableBeforePoints)
        val pointsUsed = pointsDeduction.pointsUsed
        val pointsDeduct = pointsDeduction.deductAmount
        val payAmount = payableBeforePoints.subtract(pointsDeduct).money()

        // 3. 余额扣减（需求 3.3.1：优先使用余额，不足部分由微信支付补齐）
        val payment = deductBalance(request.userId, payAmount, request.useBalance)

        // 4. 消费积分（需求 3.4.1：每消费1元积1分，基数为实付金额）
        val pointsAward = calculateConsumptionPoints(request.userId, payAmount)

        // 5. 写消费记录
        val recordId = ConsumptionRecords.insert {
            it[ConsumptionRecords.userId] = request.userId
            it[ConsumptionRecords.clinicId] = request.clinicId
            it[ConsumptionRecords.appointmentId] = request.appointmentId
            it[ConsumptionRecords.amount] = discountedAmount
            it[ConsumptionRecords.originalAmount] = originalAmount
            it[ConsumptionRecords.discountAmount] = discountAmount
            it[ConsumptionRecords.couponDeduct] = couponDeduct
            it[ConsumptionRecords.userCouponId] = coupon?.userCouponId
            it[ConsumptionRecords.balanceUsed] = payment.balanceUsed
            it[ConsumptionRecords.wechatPaid] = payment.wechatPaid
            it[ConsumptionRecords.paymentStatus] = if (payment.wechatPaid > BigDecimal.ZERO) "pending" else "success"
            it[ConsumptionRecords.payAmount] = payAmount
            it[ConsumptionRecords.pointsEarned] = pointsAward.points
            it[ConsumptionRecords.pointsUsed] = pointsUsed
            it[ConsumptionRecords.pointsDeduct] = pointsDeduct
            it[ConsumptionRecords.status] = "success"
            it[ConsumptionRecords.operatorName] = operator
            it[ConsumptionRecords.description] = request.description
        } get ConsumptionRecords.id

        if (coupon != null) {
            markCouponUsed(coupon, request.appointmentId, now)
        }

        // 资金流水：仅在实际动用了余额时记账（全额微信支付不改变钱包，无流水可言）。
        // 必须等到 recordId 产生后再写，流水才能回指本次消费记录。
        if (payment.balanceUsed > BigDecimal.ZERO) {
            recordWalletTransaction(
                userId = request.userId,
                changeType = "consume",
                amount = payment.fromBalance.negate(),
                balanceBefore = payment.balanceBefore,
                balanceAfter = payment.balanceAfter,
                bonusAmount = payment.fromBonus.negate(),
                bonusBefore = payment.bonusBalanceBefore,
                bonusAfter = payment.bonusBalanceAfter,
                // reason 列限长 200，而消费说明是不限长的 text，写入前必须截断
                reason = request.description?.take(200) ?: "消费扣款",
                operatorName = operator,
                relatedId = recordId
            )
        }

        // 积分抵扣：实际扣减本次消耗的积分（负数走同一套积分逻辑，原子计入积分流水与汇总余额）
        if (pointsUsed > 0) {
            pointsService.addPoints(
                userId = request.userId,
                amount = -pointsUsed,
                sourceType = "points_deduct",
                description = "积分抵扣诊疗费（抵 ${pointsDeduct.money()} 元）",
                sourceId = recordId
            )
        }

        val pointsBalanceAfter = if (pointsAward.points > 0) {
            pointsService.addPoints(
                userId = request.userId,
                amount = pointsAward.points,
                sourceType = "consumption",
                description = "消费获得积分",
                validityMonths = pointsAward.validityMonths,
                sourceId = recordId
            )
        } else {
            readPointsBalance(request.userId)
        }

        // 6. 累计消费并按门槛自动升级（只升不降）
        val levelChange = accumulateAndUpgradeLevel(request.userId, discountedAmount, levelSnapshot, recordId)

        // 7. 推荐奖励（需求 3.5.2：首消奖励 / 持续奖励，含月度上限）
        val referralOutcome = referralRewardService.settleConsumptionReward(
            refereeId = request.userId,
            payAmount = payAmount,
            consumptionRecordId = recordId
        )

        // 8. 就诊闭环：录入消费即视为本次就诊完成（已取消/爽约/已完成的预约不回改）
        if (appointment != null && appointment[Appointments.status] !in TERMINAL_APPOINTMENT_STATUS) {
            Appointments.update({ Appointments.id eq appointment[Appointments.id] }) {
                it[Appointments.status] = "completed"
                it[Appointments.completedAt] = now
                it[Appointments.updatedAt] = now
            }
        }

        // 9. 消费通知（需求 3.6.1 + P1-7：接入微信订阅消息，降级站内信）
        val noticeContent = buildConsumptionNotice(
            originalAmount = originalAmount,
            discountAmount = discountAmount,
            couponName = coupon?.name,
            couponDeduct = couponDeduct,
            pointsUsed = pointsUsed,
            pointsDeduct = pointsDeduct,
            payment = payment,
            payAmount = payAmount,
            pointsEarned = pointsAward.points
        )
        ScheduledNotifier.notify(
            userId = request.userId,
            templateKey = "consumption_success",
            title = "消费成功",
            content = noticeContent,
            subscribeData = mapOf(
                "thing1" to "诊疗消费",
                "amount2" to "${payAmount}元",
                "date3" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ),
            page = "/pages/consumption/detail?id=$recordId",
            msgType = "consumption",
            relatedId = recordId
        )

        ConsumptionSettlementDto(
            recordId = recordId,
            originalAmount = originalAmount.toDouble(),
            discountAmount = discountAmount.toDouble(),
            amount = discountedAmount.toDouble(),
            couponName = coupon?.name,
            couponDeduct = couponDeduct.toDouble(),
            pointsUsed = pointsUsed,
            pointsDeduct = pointsDeduct.toDouble(),
            payAmount = payAmount.toDouble(),
            balanceUsed = payment.balanceUsed.toDouble(),
            wechatPaid = payment.wechatPaid.toDouble(),
            balanceAfter = payment.balanceAfter.toDouble(),
            bonusBalanceAfter = payment.bonusBalanceAfter.toDouble(),
            pointsEarned = pointsAward.points,
            pointsBalanceAfter = pointsBalanceAfter,
            accumulatedConsumption = levelChange.accumulatedConsumption,
            levelName = levelChange.levelName,
            levelUpgraded = levelChange.upgraded,
            referralReward = toReferralSummary(referralOutcome),
            createdAt = now.format(DATE_TIME_FORMATTER)
        )
    }

    /**
     * 分页查询消费流水（需求 4.6 客户消费记录、数据看板明细下钻）。
     */
    suspend fun listConsumptions(
        userId: Long?,
        clinicId: Long?,
        startDate: String?,
        endDate: String?,
        page: Int,
        pageSize: Int,
        scope: com.dentalclinic.admin.auth.ClinicScope = com.dentalclinic.admin.auth.ClinicScope.ALL
    ): ConsumptionListResponse = dbQuery {
        // 门店数据隔离：无授权门店直接空结果
        if (scope.isEmpty) {
            return@dbQuery ConsumptionListResponse(list = emptyList(), total = 0, page = page, pageSize = pageSize)
        }
        var query = ConsumptionRecords.selectAll()

        if (userId != null) {
            query = query.andWhere { ConsumptionRecords.userId eq userId }
        }
        if (clinicId != null) {
            query = query.andWhere { ConsumptionRecords.clinicId eq clinicId }
        }
        if (!scope.all) {
            query = query.andWhere { ConsumptionRecords.clinicId inList scope.clinicIds }
        }
        if (!startDate.isNullOrBlank()) {
            val start = LocalDate.parse(startDate).atStartOfDay()
            query = query.andWhere { ConsumptionRecords.createdAt greaterEq start }
        }
        if (!endDate.isNullOrBlank()) {
            val end = LocalDate.parse(endDate).atTime(23, 59, 59)
            query = query.andWhere { ConsumptionRecords.createdAt lessEq end }
        }

        val total = query.count()
        val rows = query
            .orderBy(ConsumptionRecords.createdAt, SortOrder.DESC)
            .limit(pageSize).offset((page - 1).toLong())
            .toList()

        // 批量预加载客户与门店，避免逐行查库
        val userIds = rows.map { it[ConsumptionRecords.userId] }.distinct()
        val clinicIds = rows.map { it[ConsumptionRecords.clinicId] }.distinct()
        val usersMap = if (userIds.isNotEmpty()) {
            Users.selectAll().where { Users.id inList userIds }.toList().associateBy { it[Users.id] }
        } else emptyMap()
        val clinicsMap = if (clinicIds.isNotEmpty()) {
            Clinics.selectAll().where { Clinics.id inList clinicIds }.toList().associateBy { it[Clinics.id] }
        } else emptyMap()

        val list = rows.map { row ->
            val customer = usersMap[row[ConsumptionRecords.userId]]
            ConsumptionAdminDto(
                id = row[ConsumptionRecords.id],
                userId = row[ConsumptionRecords.userId],
                customerName = customer?.get(Users.realName) ?: customer?.get(Users.nickname),
                phone = customer?.get(Users.phone),
                clinicId = row[ConsumptionRecords.clinicId],
                clinicName = clinicsMap[row[ConsumptionRecords.clinicId]]?.get(Clinics.name),
                appointmentId = row[ConsumptionRecords.appointmentId],
                originalAmount = row[ConsumptionRecords.originalAmount].toDouble(),
                discountAmount = row[ConsumptionRecords.discountAmount].toDouble(),
                amount = row[ConsumptionRecords.amount].toDouble(),
                couponDeduct = row[ConsumptionRecords.couponDeduct].toDouble(),
                pointsUsed = row[ConsumptionRecords.pointsUsed],
                pointsDeduct = row[ConsumptionRecords.pointsDeduct].toDouble(),
                payAmount = row[ConsumptionRecords.payAmount].toDouble(),
                balanceUsed = row[ConsumptionRecords.balanceUsed].toDouble(),
                wechatPaid = row[ConsumptionRecords.wechatPaid].toDouble(),
                pointsEarned = row[ConsumptionRecords.pointsEarned],
                status = row[ConsumptionRecords.status],
                operatorName = row[ConsumptionRecords.operatorName],
                description = row[ConsumptionRecords.description],
                createdAt = row[ConsumptionRecords.createdAt].format(DATE_TIME_FORMATTER)
            )
        }

        ConsumptionListResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    // ------------------------------------------------------------------
    // 结算子步骤
    // ------------------------------------------------------------------

    /**
     * 读取客户当前会员等级与折扣率。未建档或等级已停用时按 Lv1（无折扣）处理。
     */
    private suspend fun loadMemberLevel(userId: Long): MemberLevelSnapshot {
        val userLevel = UserMemberLevels.selectAll()
            .where { UserMemberLevels.userId eq userId }
            .toList()
            .firstOrNull()

        val level = userLevel?.get(UserMemberLevels.levelId)?.let { levelId ->
            MemberLevels.selectAll()
                .where { (MemberLevels.id eq levelId) and (MemberLevels.status eq 1) }
                .toList()
                .firstOrNull()
        }

        return MemberLevelSnapshot(
            userLevel = userLevel,
            level = level,
            levelCode = level?.get(MemberLevels.levelCode) ?: DEFAULT_LEVEL_CODE,
            discountRate = level?.get(MemberLevels.discountRate) ?: BigDecimal.ONE
        )
    }

    /**
     * 扣减钱包余额（乐观锁）。
     *
     * 扣款顺序：先扣充值本金余额，再扣赠送余额——赠送余额通常附带使用限制，
     * 留到最后消耗对客户更有利。
     *
     * 余额不足时不报错：差额记为微信支付（需求 3.3.1）。真实微信支付通道尚未接入，
     * 目前先按「应收」记账，接入后在此处替换为下单+回调确认。
     */
    private suspend fun deductBalance(
        userId: Long,
        payAmount: BigDecimal,
        useBalance: Boolean
    ): PaymentSplit {
        val wallet = UserWallets.selectAll()
            .where { UserWallets.userId eq userId }
            .toList()
            .firstOrNull()

        if (!useBalance || payAmount <= BigDecimal.ZERO) {
            val balance = wallet?.get(UserWallets.balance) ?: BigDecimal.ZERO
            val bonus = wallet?.get(UserWallets.bonusBalance) ?: BigDecimal.ZERO
            return PaymentSplit(
                fromBalance = BigDecimal.ZERO,
                fromBonus = BigDecimal.ZERO,
                wechatPaid = payAmount,
                balanceBefore = balance,
                balanceAfter = balance,
                bonusBalanceBefore = bonus,
                bonusBalanceAfter = bonus
            )
        }

        if (wallet == null) {
            throw NotFoundException("客户钱包不存在，无法使用余额支付")
        }

        val currentBalance = wallet[UserWallets.balance]
        val currentBonus = wallet[UserWallets.bonusBalance]
        val fromBalance = currentBalance.min(payAmount)
        val fromBonus = currentBonus.min(payAmount.subtract(fromBalance))
        val balanceUsed = fromBalance.add(fromBonus)
        val balanceAfter = currentBalance.subtract(fromBalance)
        val bonusAfter = currentBonus.subtract(fromBonus)

        val currentVersion = wallet[UserWallets.version]
        val updated = UserWallets.update({
            (UserWallets.userId eq userId) and (UserWallets.version eq currentVersion)
        }) {
            it[UserWallets.balance] = balanceAfter
            it[UserWallets.bonusBalance] = bonusAfter
            it[UserWallets.version] = currentVersion + 1
            it[UserWallets.updatedAt] = LocalDateTime.now()
        }
        if (updated == 0) {
            throw ConflictException("客户余额已被其他操作变更，请重新提交")
        }

        return PaymentSplit(
            fromBalance = fromBalance,
            fromBonus = fromBonus,
            wechatPaid = payAmount.subtract(balanceUsed),
            balanceBefore = currentBalance,
            balanceAfter = balanceAfter,
            bonusBalanceBefore = currentBonus,
            bonusBalanceAfter = bonusAfter
        )
    }

    /**
     * 校验本次要核销的优惠券并计算抵扣金额。
     *
     * 相比 [AdminCouponService.scanAndRedeemCoupon] 的扫码核销，这里补齐了两项此前缺失的校验：
     * 使用门槛（minConsumption）与适用科室（applicableDepartments）。
     */
    private suspend fun resolveCoupon(
        userCouponId: Long,
        userId: Long,
        discountedAmount: BigDecimal,
        appointment: ResultRow?
    ): CouponRedemption {
        val userCoupon = UserCoupons.selectAll()
            .where { (UserCoupons.id eq userCouponId) and (UserCoupons.userId eq userId) }
            .toList()
            .firstOrNull()
            ?: throw NotFoundException("优惠券不存在或不属于该客户")

        if (userCoupon[UserCoupons.status] != "unused") {
            throw ValidationException("优惠券已使用或已过期，无法再次核销")
        }

        val templateId = userCoupon[UserCoupons.templateId]
        val template = CouponTemplates.selectAll()
            .where { CouponTemplates.id eq templateId }
            .toList()
            .firstOrNull()
            ?: throw NotFoundException("优惠券模板不存在")

        if (template[CouponTemplates.status] != 1) {
            throw ValidationException("「${template[CouponTemplates.name]}」已停用")
        }

        val now = LocalDateTime.now()
        if (now.isBefore(template[CouponTemplates.validFrom]) || now.isAfter(template[CouponTemplates.validTo])) {
            throw ValidationException("「${template[CouponTemplates.name]}」不在有效期内")
        }

        val minConsumption = template[CouponTemplates.minConsumption]
        if (discountedAmount < minConsumption) {
            throw ValidationException("消费金额未达「${template[CouponTemplates.name]}」的使用门槛 ${minConsumption.money()} 元")
        }

        checkApplicableDepartments(template[CouponTemplates.applicableDepartments], appointment)

        // 本次仅支持现金券按面额直减：折扣券与会员折扣的叠加口径尚未定义，服务券走预约核销
        val deduct = when (template[CouponTemplates.couponType]) {
            "cash" -> template[CouponTemplates.faceValue].min(discountedAmount)
            "discount" -> throw ValidationException("折扣券暂不支持在消费录入中抵扣，请改用现金券")
            "service" -> throw ValidationException("服务券请在预约核销时使用，不参与金额抵扣")
            else -> throw ValidationException("未知的优惠券类型：${template[CouponTemplates.couponType]}")
        }

        return CouponRedemption(
            userCouponId = userCouponId,
            templateId = templateId,
            name = template[CouponTemplates.name],
            deduct = deduct.money(),
            templateUsedQuantity = template[CouponTemplates.usedQuantity]
        )
    }

    /**
     * 适用科室校验。applicableDepartments 存 JSON 数组（如 `[1,2]`），为空表示不限科室。
     * 限定了科室却拿不到本次就诊科室时直接拒绝，避免超范围核销。
     */
    private fun checkApplicableDepartments(raw: String?, appointment: ResultRow?) {
        val departmentIds = parseDepartmentIds(raw)
        if (departmentIds.isEmpty()) return

        val departmentId = appointment?.get(Appointments.departmentId)
            ?: throw ValidationException("该优惠券限定科室使用，请先关联本次就诊的预约记录")
        if (departmentId !in departmentIds) {
            throw ValidationException("本次就诊科室不在该优惠券的适用范围内")
        }
    }

    /** 解析适用科室配置；格式异常时按不限科室处理并告警，避免因脏配置阻塞收银。 */
    private fun parseDepartmentIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }
        }.getOrElse {
            logger.warn("[Consumption] 优惠券适用科室配置解析失败，按不限科室处理：$raw")
            emptyList()
        }
    }

    /** 核销优惠券：标记已用并累加模板使用量。 */
    private suspend fun markCouponUsed(coupon: CouponRedemption, appointmentId: Long?, now: LocalDateTime) {
        UserCoupons.update({ UserCoupons.id eq coupon.userCouponId }) {
            it[UserCoupons.status] = "used"
            it[UserCoupons.usedAt] = now
            it[UserCoupons.appointmentId] = appointmentId
        }
        CouponTemplates.update({ CouponTemplates.id eq coupon.templateId }) {
            it[CouponTemplates.usedQuantity] = coupon.templateUsedQuantity + 1
        }
    }

    /**
     * 计算消费积分（需求 3.4.1：每消费 1 元积 1 分，含余额支付和微信支付）。
     *
     * 基数取实付金额；规则若配置了每日/每月上限，则按剩余额度截断。
     */
    private suspend fun calculateConsumptionPoints(userId: Long, payAmount: BigDecimal): PointsAward {
        // 按 id 升序取第一条：action_type 已建唯一索引，正常不会有同类型多行，
        // 但旧库可能残留重复数据，指定顺序可避免命中结果随查询顺序漂移
        val rule = PointsRules.selectAll()
            .where { (PointsRules.actionType eq "consumption") and (PointsRules.status eq 1) }
            .orderBy(PointsRules.id, SortOrder.ASC)
            .toList()
            .firstOrNull()
            ?: return PointsAward(0, DEFAULT_VALIDITY_MONTHS)

        val validityMonths = rule[PointsRules.validityMonths]
        // 每 unitAmount 元积 pointsPerUnit 分，不足一个计分单位的部分不计
        val raw = calculateRulePoints(payAmount, rule[PointsRules.pointsPerUnit], rule[PointsRules.unitAmount])
        if (raw <= 0) return PointsAward(0, validityMonths)

        val cappedByDay = minOf(raw, remainingQuota(userId, "consumption", rule[PointsRules.dailyLimit], daily = true))
        val points = minOf(cappedByDay, remainingQuota(userId, "consumption", rule[PointsRules.monthlyLimit], daily = false))
        return PointsAward(points, validityMonths)
    }

    /** 某来源积分在当日/当月的剩余可发额度；未配置上限时不截断。 */
    private suspend fun remainingQuota(userId: Long, sourceType: String, limit: Int?, daily: Boolean): Int {
        if (limit == null || limit <= 0) return Int.MAX_VALUE

        val periodStart = if (daily) {
            LocalDate.now().atStartOfDay()
        } else {
            LocalDate.now().withDayOfMonth(1).atStartOfDay()
        }
        val granted = PointsRecords.selectAll()
            .where {
                (PointsRecords.userId eq userId) and
                    (PointsRecords.sourceType eq sourceType) and
                    (PointsRecords.createdAt greaterEq periodStart)
            }
            .toList()
            .sumOf { it[PointsRecords.changeAmount] }

        return (limit - granted).coerceAtLeast(0)
    }

    private suspend fun readPointsBalance(userId: Long): Int =
        UserPointsSummary.selectAll()
            .where { UserPointsSummary.userId eq userId }
            .toList()
            .singleOrNull()
            ?.get(UserPointsSummary.totalBalance) ?: 0

    /**
     * 计算本笔消费的积分抵扣（需求 3.4.2：100 积分抵 1 元，单笔最高抵扣应付金额的一定比例）。
     *
     * 抵扣基数为「会员折扣 + 优惠券之后、余额支付之前」的应付金额 [payable]，
     * 与「单笔最高抵扣消费金额的 20%」口径一致。比率与上限均由
     * [PointsDeductConfig] 驱动，管理端可热改。
     *
     * 纯校验+换算，不改库：实际扣分在消费记录落库后由结算主流程执行。
     */
    private suspend fun resolvePointsDeduction(
        userId: Long,
        usePoints: Int,
        payable: BigDecimal
    ): PointsDeduction {
        if (usePoints <= 0 || payable <= BigDecimal.ZERO) {
            return PointsDeduction(0, BigDecimal.ZERO)
        }
        if (!PointsDeductConfig.enabled()) {
            throw ValidationException("积分抵扣功能当前已关闭，请联系管理员或取消积分抵扣")
        }

        val perYuan = PointsDeductConfig.pointsPerYuan()
        val maxRatioPercent = PointsDeductConfig.maxRatioPercent()

        val balance = readPointsBalance(userId)
        if (balance <= 0) {
            throw ValidationException("该客户当前无可用积分，无法使用积分抵扣")
        }

        // 比例上限换算为积分数：payable × maxRatio% × perYuan，向下取整避免超比例
        val maxPointsByRatio = payable
            .multiply(BigDecimal(maxRatioPercent))
            .multiply(BigDecimal(perYuan))
            .divide(BigDecimal(100), 0, RoundingMode.DOWN)
            .toInt()

        val pointsUsed = minOf(usePoints, balance, maxPointsByRatio)
        if (pointsUsed <= 0) {
            throw ValidationException("本次可抵扣额度为 0，无法使用积分抵扣（单笔最高抵扣应付金额的 $maxRatioPercent%）")
        }

        val deductAmount = BigDecimal(pointsUsed)
            .divide(BigDecimal(perYuan), 2, RoundingMode.DOWN)

        return PointsDeduction(pointsUsed = pointsUsed, deductAmount = deductAmount)
    }

    /**
     * 累加消费并按门槛自动升级会员等级（需求 3.3.2）。
     *
     * 累计口径用折后消费金额 [ConsumptionRecords.amount]，与数据看板营收口径一致。
     * 只升不降：降级由「每年 1 月 1 日按上年度消费重新评定」的定时任务负责，
     * 消费链路不做降级，避免客户被单笔小额消费误降。
     */
    private suspend fun accumulateAndUpgradeLevel(
        userId: Long,
        amount: BigDecimal,
        snapshot: MemberLevelSnapshot,
        consumptionRecordId: Long
    ): LevelChangeResult {
        val now = LocalDateTime.now()
        val accumulated = (snapshot.userLevel?.get(UserMemberLevels.accumulatedConsumption) ?: BigDecimal.ZERO)
            .add(amount)

        // 达标的最高等级（levelCode 倒序取第一个满足门槛的）
        val qualifiedLevel = MemberLevels.selectAll()
            .where { MemberLevels.status eq 1 }
            .orderBy(MemberLevels.levelCode, SortOrder.DESC)
            .toList()
            .firstOrNull { accumulated >= it[MemberLevels.minConsumption] }

        if (snapshot.userLevel == null) {
            // 首次建档：无达标等级时兜底为 Lv1（注册即为普通会员）
            val levelRow = qualifiedLevel ?: MemberLevels.selectAll()
                .where { (MemberLevels.levelCode eq DEFAULT_LEVEL_CODE) and (MemberLevels.status eq 1) }
                .toList()
                .firstOrNull()
                ?: return LevelChangeResult(accumulated.toDouble(), null, false)

            UserMemberLevels.insert {
                it[UserMemberLevels.userId] = userId
                it[UserMemberLevels.levelId] = levelRow[MemberLevels.id]
                it[UserMemberLevels.accumulatedConsumption] = accumulated
                it[UserMemberLevels.effectiveFrom] = now
            }

            // Lv1 是注册默认等级，不算升级
            val upgraded = levelRow[MemberLevels.levelCode] > DEFAULT_LEVEL_CODE
            if (upgraded) notifyLevelChange(userId, levelRow, accumulated, consumptionRecordId)
            return LevelChangeResult(accumulated.toDouble(), levelRow[MemberLevels.levelName], upgraded)
        }

        val upgradeTo = qualifiedLevel?.takeIf { it[MemberLevels.levelCode] > snapshot.levelCode }

        // 即使不升级，累计消费也必须落库，否则下次判定会漏算
        UserMemberLevels.update({ UserMemberLevels.userId eq userId }) {
            it[UserMemberLevels.accumulatedConsumption] = accumulated
            if (upgradeTo != null) {
                it[UserMemberLevels.levelId] = upgradeTo[MemberLevels.id]
                it[UserMemberLevels.effectiveFrom] = now
            }
            it[UserMemberLevels.updatedAt] = now
        }

        if (upgradeTo != null) {
            notifyLevelChange(userId, upgradeTo, accumulated, consumptionRecordId)
        }

        return LevelChangeResult(
            accumulatedConsumption = accumulated.toDouble(),
            levelName = upgradeTo?.get(MemberLevels.levelName) ?: snapshot.level?.get(MemberLevels.levelName),
            upgraded = upgradeTo != null
        )
    }

    /**
     * 等级变动通知。需求 3.6.1 指定的渠道是微信模板消息，模板消息通道尚未接入，
     * 此处先落站内消息；接入后在同一位置补发即可。
     */
    private suspend fun notifyLevelChange(
        userId: Long,
        level: ResultRow,
        accumulated: BigDecimal,
        relatedId: Long
    ) {
        // 微信订阅消息优先，未配置/未授权时自动降级站内信
        ScheduledNotifier.notify(
            userId = userId,
            templateKey = "level_change",
            title = "会员等级提升",
            content = "恭喜！您的累计消费已达 ${accumulated.money()} 元，会员等级提升为" +
                "「${level[MemberLevels.levelName]}」，可享 ${formatDiscount(level[MemberLevels.discountRate])} 诊疗折扣。",
            subscribeData = mapOf(
                "thing1" to level[MemberLevels.levelName],
                "thing2" to "等级提升"
            ),
            page = "/pages/profile/profile",
            msgType = "system",
            relatedId = relatedId
        )
    }

    /** 折扣率转中文表述：0.95 → 9.5折，0.90 → 9折。 */
    private fun formatDiscount(rate: BigDecimal): String =
        rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + "折"

    /** 组装消费通知正文（需求 3.6.1 要求包含消费金额、余额、积分变动）。 */
    private fun buildConsumptionNotice(
        originalAmount: BigDecimal,
        discountAmount: BigDecimal,
        couponName: String?,
        couponDeduct: BigDecimal,
        pointsUsed: Int,
        pointsDeduct: BigDecimal,
        payment: PaymentSplit,
        payAmount: BigDecimal,
        pointsEarned: Int
    ): String = buildString {
        append("本次消费 ${payAmount.money()} 元")
        if (discountAmount > BigDecimal.ZERO) {
            append("（原价 ${originalAmount.money()} 元，会员折扣 -${discountAmount.money()} 元）")
        }
        if (couponDeduct > BigDecimal.ZERO) {
            append("，优惠券「$couponName」抵扣 -${couponDeduct.money()} 元")
        }
        if (pointsDeduct > BigDecimal.ZERO) {
            append("，积分抵扣 -${pointsDeduct.money()} 元（消耗 $pointsUsed 分）")
        }
        append("。")
        if (payment.balanceUsed > BigDecimal.ZERO) append("余额支付 ${payment.balanceUsed.money()} 元，")
        if (payment.wechatPaid > BigDecimal.ZERO) append("微信支付 ${payment.wechatPaid.money()} 元，")
        if (pointsEarned > 0) append("获得 $pointsEarned 积分，")
        append("当前可用余额 ${(payment.balanceAfter + payment.bonusBalanceAfter).money()} 元。")
    }

    private fun toReferralSummary(outcome: ReferralRewardOutcome): ReferralRewardSummary? = when (outcome) {
        ReferralRewardOutcome.NoRelation -> null
        is ReferralRewardOutcome.FirstConsumption -> ReferralRewardSummary(
            stage = "first_consumption",
            referrerId = outcome.referrerId,
            points = outcome.points,
            bonusBalance = outcome.bonusBalance,
            newcomerCouponValue = outcome.newcomerCouponValue
        )
        is ReferralRewardOutcome.Ongoing -> ReferralRewardSummary(
            stage = "ongoing",
            referrerId = outcome.referrerId,
            points = outcome.points
        )
        is ReferralRewardOutcome.NoReward -> ReferralRewardSummary(
            stage = "none",
            referrerId = outcome.referrerId,
            remark = outcome.reason
        )
    }

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** Lv1 为注册默认等级 */
        private const val DEFAULT_LEVEL_CODE = 1
        private const val DEFAULT_VALIDITY_MONTHS = 12

        /** 这些状态的预约不再被消费录入回改为已完成 */
        private val TERMINAL_APPOINTMENT_STATUS = setOf("completed", "cancelled", "no_show")
    }
}

/** 金额统一保留两位小数（四舍五入），避免浮点误差进入账目字段。 */
private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

// ------------------------------------------------------------------
// 内部值对象
// ------------------------------------------------------------------

/** 客户当前会员等级快照 */
private data class MemberLevelSnapshot(
    val userLevel: ResultRow?,
    val level: ResultRow?,
    val levelCode: Int,
    val discountRate: BigDecimal
)

/**
 * 实付金额的支付拆分。
 *
 * 本金与赠送余额分开记录（fromBalance/fromBonus），使资金流水能区分两者的出账；
 * before 快照同样只为流水记账服务。
 */
private data class PaymentSplit(
    val fromBalance: BigDecimal,
    val fromBonus: BigDecimal,
    val wechatPaid: BigDecimal,
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val bonusBalanceBefore: BigDecimal,
    val bonusBalanceAfter: BigDecimal
) {
    /** 本次动用的余额合计（本金 + 赠送） */
    val balanceUsed: BigDecimal get() = fromBalance.add(fromBonus)
}

/** 通过校验、待核销的优惠券 */
private data class CouponRedemption(
    val userCouponId: Long,
    val templateId: Long,
    val name: String,
    val deduct: BigDecimal,
    val templateUsedQuantity: Int
)

/** 本次消费应得积分与有效期 */
private data class PointsAward(
    val points: Int,
    val validityMonths: Int
)

/** 积分抵扣诊疗费计算结果（需求 3.4.2） */
private data class PointsDeduction(
    val pointsUsed: Int,
    val deductAmount: BigDecimal
)

/** 累计消费与等级变动结果 */
private data class LevelChangeResult(
    val accumulatedConsumption: Double,
    val levelName: String?,
    val upgraded: Boolean
)

// ------------------------------------------------------------------
// DTO
// ------------------------------------------------------------------

@Serializable
data class CreateConsumptionRequest(
    val userId: Long,
    val clinicId: Long,
    val appointmentId: Long? = null,
    /** 项目原价（未打折） */
    val originalAmount: Double,
    /** 选用的用户优惠券ID，不传表示不使用优惠券 */
    val userCouponId: Long? = null,
    /** 是否优先使用钱包余额支付；false 表示全额记为微信支付 */
    val useBalance: Boolean = true,
    /** 本次要消耗的积分数量（需求 3.4.2 积分抵扣诊疗费）；0 或不传表示不使用积分抵扣 */
    val usePoints: Int = 0,
    val description: String? = null
)

@Serializable
data class ReferralRewardSummary(
    /** first_consumption / ongoing / none */
    val stage: String,
    val referrerId: Long? = null,
    val points: Int = 0,
    val bonusBalance: Double = 0.0,
    val newcomerCouponValue: Double? = null,
    val remark: String? = null
)

@Serializable
data class ConsumptionSettlementDto(
    val recordId: Long,
    val originalAmount: Double,
    val discountAmount: Double,
    val amount: Double,
    val couponName: String?,
    val couponDeduct: Double,
    val pointsUsed: Int,
    val pointsDeduct: Double,
    val payAmount: Double,
    val balanceUsed: Double,
    val wechatPaid: Double,
    val balanceAfter: Double,
    val bonusBalanceAfter: Double,
    val pointsEarned: Int,
    val pointsBalanceAfter: Int,
    val accumulatedConsumption: Double,
    val levelName: String?,
    val levelUpgraded: Boolean,
    val referralReward: ReferralRewardSummary?,
    val createdAt: String
)

@Serializable
data class ConsumptionAdminDto(
    val id: Long,
    val userId: Long,
    val customerName: String?,
    val phone: String?,
    val clinicId: Long,
    val clinicName: String?,
    val appointmentId: Long?,
    val originalAmount: Double,
    val discountAmount: Double,
    val amount: Double,
    val couponDeduct: Double,
    val pointsUsed: Int,
    val pointsDeduct: Double,
    val payAmount: Double,
    val balanceUsed: Double,
    val wechatPaid: Double,
    val pointsEarned: Int,
    val status: String,
    val operatorName: String?,
    val description: String?,
    val createdAt: String
)

@Serializable
data class ConsumptionListResponse(
    val list: List<ConsumptionAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)
