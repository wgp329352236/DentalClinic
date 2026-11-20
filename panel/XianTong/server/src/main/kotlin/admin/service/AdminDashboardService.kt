package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.ClinicScope
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 数据看板服务（需求 4.10）。
 *
 * 统计口径统一约定，避免同一指标在不同页面给出不同数字：
 * - 「预约数」按 [Appointments.appointmentDate]（预约日期）统计，并排除 cancelled。
 *   即「这一天有多少客人要来」，而不是「这一天新下了多少单」——后者对排班和接诊无参考意义。
 * - 「营收」只计 [ConsumptionRecords.status] 为 success 的记录，取折后金额 amount，
 *   与 BusinessServices 的客户累计消费、会员等级升级判定同口径（已退款不算营收）。
 * - 「新客数」按 [Users.createdAt]（建档时间）统计。
 *
 * 门店隔离：所有统计均接受 [ClinicScope]。无全店权限的账号只统计其授权门店的数据：
 * 预约/营收按业务行的 clinicId 过滤，客户类指标按 Users.primaryClinicId 过滤。
 *
 * 性能：需求 5.1 要求看板查询响应不超过 3 秒，因此所有分组统计都采用
 * 「一次查询 + 内存聚合」（项目既有惯例，见 BusinessServices 的客户列表），
 * 不做逐天/逐门店的循环查询——那会把趋势接口放大成 3×天数 次查询（90 天即 270 次）。
 */
class AdminDashboardService : BaseService() {

    /**
     * 实时概览：今日预约数、到店数、营收、新增客户
     */
    suspend fun getSummary(scope: ClinicScope = ClinicScope.ALL): DashboardSummary {
        if (scope.isEmpty) {
            return DashboardSummary(
                todayAppointments = 0,
                todayArrivals = 0,
                todayRevenue = 0.0,
                todayNewCustomers = 0
            )
        }
        return dbQuery {
            val today = LocalDate.now()
            val todayStart = today.atStartOfDay()
            val todayEnd = today.plusDays(1).atStartOfDay()

            // 今日预约数（当天应到，排除已取消）
            val todayAppointments = Appointments.selectAll()
                .where {
                    (Appointments.appointmentDate eq today) and
                            (Appointments.status neq "cancelled")
                }
                .scopeClinic(scope, Appointments.clinicId)
                .count().toInt()

            // 今日到店数：按实际到店时间统计，与预约日期无关（可能存在跨天补录）
            val todayArrivals = Appointments.selectAll()
                .where {
                    (Appointments.arrivedAt greaterEq todayStart) and
                            (Appointments.arrivedAt less todayEnd)
                }
                .scopeClinic(scope, Appointments.clinicId)
                .count().toInt()

            val todayRevenue = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.createdAt greaterEq todayStart) and
                            (ConsumptionRecords.createdAt less todayEnd) and
                            (ConsumptionRecords.status eq "success") and (ConsumptionRecords.paymentStatus eq "success")
                }
                .scopeClinic(scope, ConsumptionRecords.clinicId)
                .toList()
                .sumOf { it[ConsumptionRecords.amount].toDouble() }

            val todayNewCustomers = Users.selectAll()
                .where {
                    (Users.createdAt greaterEq todayStart) and
                            (Users.createdAt less todayEnd)
                }
                .scopeClinic(scope, Users.primaryClinicId)
                .count().toInt()

            DashboardSummary(
                todayAppointments = todayAppointments,
                todayArrivals = todayArrivals,
                todayRevenue = todayRevenue.round2(),
                todayNewCustomers = todayNewCustomers
            )
        }
    }

    /**
     * 门店对比：本月各门店的预约量、营收、新客数
     *
     * customerSatisfaction 恒为 null：满意度依赖客户评价功能，该功能尚未实现
     * （需求 4.10 列了「客户满意度对比」，但 3.x 的用户端功能清单里没有评价模块）。
     * 保留字段以便评价功能上线后直接填充，前端按「暂无数据」展示。
     */
    suspend fun getClinicComparison(scope: ClinicScope = ClinicScope.ALL): List<ClinicComparison> {
        if (scope.isEmpty) return emptyList()
        return dbQuery {
            val clinics = Clinics.selectAll()
                .scopeClinic(scope, Clinics.id)
                .orderBy(Clinics.sortOrder, SortOrder.ASC)
                .toList()
            if (clinics.isEmpty()) return@dbQuery emptyList()

            val monthStart = LocalDate.now().withDayOfMonth(1)
            val monthStartTime = monthStart.atStartOfDay()

            val appointmentsByClinic = Appointments.selectAll()
                .where {
                    (Appointments.appointmentDate greaterEq monthStart) and
                            (Appointments.status neq "cancelled")
                }
                .scopeClinic(scope, Appointments.clinicId)
                .toList()
                .groupBy { it[Appointments.clinicId] }
                .mapValues { (_, rows) -> rows.size }

            val revenueByClinic = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.createdAt greaterEq monthStartTime) and
                            (ConsumptionRecords.status eq "success") and (ConsumptionRecords.paymentStatus eq "success")
                }
                .scopeClinic(scope, ConsumptionRecords.clinicId)
                .toList()
                .groupBy { it[ConsumptionRecords.clinicId] }
                .mapValues { (_, rows) -> rows.sumOf { it[ConsumptionRecords.amount].toDouble() } }

            // 新客归属门店取 primaryClinicId（首选就诊门店）；该列可空，未指定门店的新客不落到任何门店
            val newCustomersByClinic = Users.selectAll()
                .where { Users.createdAt greaterEq monthStartTime }
                .scopeClinic(scope, Users.primaryClinicId)
                .toList()
                .groupBy { it[Users.primaryClinicId] }
                .mapValues { (_, rows) -> rows.size }

            clinics.map { clinicRow ->
                val clinicId = clinicRow[Clinics.id]
                ClinicComparison(
                    clinicId = clinicId,
                    clinicName = clinicRow[Clinics.name],
                    appointmentCount = appointmentsByClinic[clinicId] ?: 0,
                    revenue = (revenueByClinic[clinicId] ?: 0.0).round2(),
                    newCustomerCount = newCustomersByClinic[clinicId] ?: 0,
                    customerSatisfaction = null
                )
            }
        }
    }

    /**
     * 趋势数据：近 N 天的预约、营收、新客趋势
     *
     * 返回连续的 N 天（含无数据的日期，值为 0），保证折线图横轴不断点。
     *
     * @param days 天数，路由层已 coerceIn(7, 90)
     */
    suspend fun getTrends(days: Int = 30, scope: ClinicScope = ClinicScope.ALL): List<TrendDataPoint> {
        if (scope.isEmpty) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val today = LocalDate.now()
            return (0 until days).map { i ->
                TrendDataPoint(
                    date = today.minusDays((days - 1 - i).toLong()).format(formatter),
                    appointmentCount = 0,
                    revenue = 0.0,
                    newCustomerCount = 0
                )
            }
        }
        return dbQuery {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong() - 1)
            val rangeStart = startDate.atStartOfDay()
            val rangeEnd = endDate.plusDays(1).atStartOfDay()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val appointmentsByDay = Appointments.selectAll()
                .where {
                    (Appointments.appointmentDate greaterEq startDate) and
                            (Appointments.appointmentDate lessEq endDate) and
                            (Appointments.status neq "cancelled")
                }
                .scopeClinic(scope, Appointments.clinicId)
                .toList()
                .groupBy { it[Appointments.appointmentDate] }
                .mapValues { (_, rows) -> rows.size }

            val revenueByDay = ConsumptionRecords.selectAll()
                .where {
                    (ConsumptionRecords.createdAt greaterEq rangeStart) and
                            (ConsumptionRecords.createdAt less rangeEnd) and
                            (ConsumptionRecords.status eq "success") and (ConsumptionRecords.paymentStatus eq "success")
                }
                .scopeClinic(scope, ConsumptionRecords.clinicId)
                .toList()
                .groupBy { it[ConsumptionRecords.createdAt].toLocalDate() }
                .mapValues { (_, rows) -> rows.sumOf { it[ConsumptionRecords.amount].toDouble() } }

            val newCustomersByDay = Users.selectAll()
                .where {
                    (Users.createdAt greaterEq rangeStart) and
                            (Users.createdAt less rangeEnd)
                }
                .scopeClinic(scope, Users.primaryClinicId)
                .toList()
                .groupBy { it[Users.createdAt].toLocalDate() }
                .mapValues { (_, rows) -> rows.size }

            (0 until days).map { i ->
                val date = startDate.plusDays(i.toLong())
                TrendDataPoint(
                    date = date.format(formatter),
                    appointmentCount = appointmentsByDay[date] ?: 0,
                    revenue = (revenueByDay[date] ?: 0.0).round2(),
                    newCustomerCount = newCustomersByDay[date] ?: 0
                )
            }
        }
    }

    /**
     * 客户等级分布：各会员等级下的客户数
     */
    suspend fun getCustomerLevelDistribution(scope: ClinicScope = ClinicScope.ALL): List<CustomerLevelDistribution> {
        if (scope.isEmpty) return emptyList()
        return dbQuery {
            val now = LocalDateTime.now()
            val levels = MemberLevels.selectAll()
                .where { MemberLevels.status eq 1 }
                .orderBy(MemberLevels.levelCode, SortOrder.ASC)
                .toList()

            // 门店范围下的客户 ID 集合（scope.all 时为 null，不加客户过滤）
            val scopedUserIds: List<Long>? = if (scope.all) null else Users.selectAll()
                .where { Users.primaryClinicId inList scope.clinicIds }
                .toList()
                .map { it[Users.id] }

            // 授权门店下一个客户都没有：各等级计数均为 0，仍返回完整等级序列给前端画图例
            if (scopedUserIds != null && scopedUserIds.isEmpty()) {
                return@dbQuery levels.map { levelRow ->
                    CustomerLevelDistribution(
                        levelCode = levelRow[MemberLevels.levelCode],
                        levelName = levelRow[MemberLevels.levelName],
                        count = 0
                    )
                }
            }

            // 只统计当前生效的等级绑定：effectiveTo 为空表示长期有效。
            // 不加这个过滤会把历史升降级留下的过期记录一并计入，
            // 同一个客户可能同时出现在两个等级里，分布总数超过实际客户数。
            var bindingQuery = UserMemberLevels.selectAll()
                .where {
                    (UserMemberLevels.effectiveFrom lessEq now) and
                            (UserMemberLevels.effectiveTo.isNull() or
                                    (UserMemberLevels.effectiveTo greater now))
                }
            if (scopedUserIds != null) {
                bindingQuery = bindingQuery.andWhere { UserMemberLevels.userId inList scopedUserIds }
            }
            val activeBindings = bindingQuery
                .toList()
                .groupBy { it[UserMemberLevels.levelId] }
                .mapValues { (_, rows) -> rows.size }

            levels.map { levelRow ->
                CustomerLevelDistribution(
                    levelCode = levelRow[MemberLevels.levelCode],
                    levelName = levelRow[MemberLevels.levelName],
                    count = activeBindings[levelRow[MemberLevels.id]] ?: 0
                )
            }
        }
    }

    /**
     * 客户来源分布：自主注册 / 推荐注册 / 门店录入
     */
    suspend fun getCustomerSourceDistribution(scope: ClinicScope = ClinicScope.ALL): List<CustomerSourceDistribution> {
        if (scope.isEmpty) {
            // 仍返回三个来源桶（值为 0），前端饼图可正常渲染空态
            return listOf(
                CustomerSourceDistribution("self", "自主注册", 0, 0.0),
                CustomerSourceDistribution("referral", "推荐注册", 0, 0.0),
                CustomerSourceDistribution("store_entry", "门店录入", 0, 0.0)
            )
        }
        return dbQuery {
            val sourceTypes = listOf(
                "self" to "自主注册",
                "referral" to "推荐注册",
                "store_entry" to "门店录入"
            )

            // 一次取出范围内全部用户的来源后内存计数，避免每个来源一次查询
            val scopedUsers = Users.selectAll()
                .scopeClinic(scope, Users.primaryClinicId)
                .toList()
            val totalUsers = scopedUsers.size
            if (totalUsers == 0) {
                return@dbQuery sourceTypes.map { (sourceType, label) ->
                    CustomerSourceDistribution(sourceType, label, 0, 0.0)
                }
            }

            val countBySource = scopedUsers
                .groupBy { it[Users.sourceType] }
                .mapValues { (_, rows) -> rows.size }

            sourceTypes.map { (sourceType, label) ->
                val count = countBySource[sourceType] ?: 0
                CustomerSourceDistribution(
                    sourceType = sourceType,
                    sourceLabel = label,
                    count = count,
                    percentage = (count.toDouble() / totalUsers * 100).round2()
                )
            }
        }
    }

    /**
     * 推荐统计：注册转化率、推荐人 TOP10、月度新客数与营收贡献
     *
     * 门店口径：推荐关系按「被推荐人归属门店」（referee.primaryClinicId）收敛；
     * 月度营收按消费记录的 clinicId 收敛，与其他营收指标同口径。
     */
    suspend fun getReferralStats(scope: ClinicScope = ClinicScope.ALL): ReferralStats {
        if (scope.isEmpty) {
            return ReferralStats(
                conversionRate = 0.0,
                topReferrers = emptyList(),
                monthlyNewCustomers = 0,
                monthlyRevenue = 0.0
            )
        }
        return dbQuery {
            // 排除自己推荐自己：与 AdminReferralService 的统计口径一致，这类关系是无效数据
            var relationQuery = ReferralRelations.selectAll()
                .where { ReferralRelations.referrerId neq ReferralRelations.refereeId }
            if (!scope.all) {
                // 先取授权门店客户集合，再收敛被推荐人
                val scopedUserIds = Users.selectAll()
                    .where { Users.primaryClinicId inList scope.clinicIds }
                    .toList()
                    .map { it[Users.id] }
                if (scopedUserIds.isEmpty()) {
                    return@dbQuery ReferralStats(0.0, emptyList(), 0, 0.0)
                }
                relationQuery = relationQuery.andWhere {
                    ReferralRelations.refereeId inList scopedUserIds
                }
            }
            val allRelations = relationQuery.toList()

            val totalReferees = allRelations.size
            // 转化率 = 已完成首次消费的被推荐人 / 总被推荐人
            val convertedReferees = allRelations.count { it[ReferralRelations.firstConsumedAt] != null }
            val conversionRate = if (totalReferees > 0) {
                (convertedReferees.toDouble() / totalReferees * 100).round2()
            } else 0.0

            // 推荐人 TOP10：按被推荐人数降序，人数相同时按推荐人 ID 升序，保证结果稳定不随查询顺序漂移
            val referrerCounts = allRelations.groupBy { it[ReferralRelations.referrerId] }
                .mapValues { (_, rows) -> rows.size }
                .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
                .take(10)

            // 昵称一次性批量取回，不逐个推荐人查一次
            val referrerIds = referrerCounts.map { it.key }
            val nicknameById = if (referrerIds.isNotEmpty()) {
                Users.selectAll()
                    .where { Users.id inList referrerIds }
                    .toList()
                    .associate { it[Users.id] to it[Users.nickname] }
            } else emptyMap()

            val topReferrers = referrerCounts.map { (referrerId, count) ->
                TopReferrer(
                    userId = referrerId,
                    nickname = nicknameById[referrerId],
                    referralCount = count
                )
            }

            val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()

            // 月度新客数 = 本月建立的推荐关系数（被推荐人在本月注册）
            val monthlyNewCustomers = allRelations.count { it[ReferralRelations.registeredAt] >= monthStart }

            // 月度营收贡献 = 全部被推荐客户本月在授权门店的成功消费额。
            // 不按「本月才首次消费」筛选：老被推荐客户的持续消费同样是推荐带来的贡献，
            // 只算新转化的会系统性低估推荐渠道的价值。
            val refereeIds = allRelations.map { it[ReferralRelations.refereeId] }.distinct()
            val monthlyRevenue = if (refereeIds.isNotEmpty()) {
                ConsumptionRecords.selectAll()
                    .where {
                        (ConsumptionRecords.userId inList refereeIds) and
                                (ConsumptionRecords.createdAt greaterEq monthStart) and
                                (ConsumptionRecords.status eq "success") and (ConsumptionRecords.paymentStatus eq "success")
                    }
                    .scopeClinic(scope, ConsumptionRecords.clinicId)
                    .toList()
                    .sumOf { it[ConsumptionRecords.amount].toDouble() }
            } else 0.0

            ReferralStats(
                conversionRate = conversionRate,
                topReferrers = topReferrers,
                monthlyNewCustomers = monthlyNewCustomers,
                monthlyRevenue = monthlyRevenue.round2()
            )
        }
    }

    /**
     * 客户留存：复诊率、流失率
     */
    suspend fun getCustomerRetention(scope: ClinicScope = ClinicScope.ALL): CustomerRetention {
        if (scope.isEmpty) return CustomerRetention(0.0, 0.0)
        return dbQuery {
            // 门店范围客户集合：分母分子都限定在同一客户集内，避免跨店就诊客户串口径
            val scopedUsers = Users.selectAll()
                .scopeClinic(scope, Users.primaryClinicId)
                .toList()
            val totalCustomers = scopedUsers.size
            if (totalCustomers == 0) return@dbQuery CustomerRetention(0.0, 0.0)
            val scopedUserIds = scopedUsers.map { it[Users.id] }

            // 复诊率 = 完成就诊 ≥2 次的客户 / 总客户（预约按门店 + 客户双重收敛）
            val completedByUser = Appointments.selectAll()
                .where { Appointments.status eq "completed" }
                .scopeClinic(scope, Appointments.clinicId)
                .andWhere { Appointments.userId inList scopedUserIds }
                .toList()
                .groupBy { it[Appointments.userId] }
            val repeatVisitCustomers = completedByUser.count { (_, rows) -> rows.size >= 2 }
            val repeatVisitRate = (repeatVisitCustomers.toDouble() / totalCustomers * 100).round2()

            // 流失率 = 近 90 天无活跃的客户 / 总客户
            val ninetyDaysAgo = LocalDate.now().minusDays(90).atStartOfDay()
            val activeByLogin = scopedUsers.count { row ->
                row[Users.lastLoginAt] != null && row[Users.lastLoginAt]!! >= ninetyDaysAgo
            }
            // lastLoginAt 为空的多是门店录入建档、从未登录过小程序的账号：
            // SQL 里 NULL 参与比较结果为 NULL，会被直接判成非活跃。建档 90 天内的仍算活跃客户，
            // 否则刚录入的新客户会立刻被算成流失，流失率虚高。
            val activeByRegister = scopedUsers.count { row ->
                row[Users.lastLoginAt] == null && row[Users.createdAt] >= ninetyDaysAgo
            }
            val churnedCustomers = (totalCustomers - activeByLogin - activeByRegister).coerceAtLeast(0)
            val churnRate = (churnedCustomers.toDouble() / totalCustomers * 100).round2()

            CustomerRetention(
                repeatVisitRate = repeatVisitRate,
                churnRate = churnRate
            )
        }
    }

    /** 金额与百分比统一保留两位小数：Double 直除会产生 9.090909090909092 这类长尾。 */
    private fun Double.round2(): Double = Math.round(this * 100) / 100.0

    /**
     * 非全店范围时给查询追加「门店列 IN 授权门店」过滤；全店范围原样返回。
     * 列的可空性不影响过滤语义（[Users.primaryClinicId] 可空，其余门店列非空），
     * 泛型擦除后运行时行为一致。
     */
    @Suppress("UNCHECKED_CAST")
    private fun Query.scopeClinic(scope: ClinicScope, column: ExpressionWithColumnType<*>): Query =
        if (scope.all) this
        else andWhere { (column as ExpressionWithColumnType<Long?>).inList(scope.clinicIds) }
}
