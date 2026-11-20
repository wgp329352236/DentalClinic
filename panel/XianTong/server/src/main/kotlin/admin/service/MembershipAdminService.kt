package com.dentalclinic.admin.service

import com.dentalclinic.admin.auth.ClinicScope
import com.dentalclinic.admin.dto.request.CustomerTagUpsertRequest
import com.dentalclinic.admin.dto.request.MemberLevelUpsertRequest
import com.dentalclinic.admin.dto.request.MessageTemplateUpsertRequest
import com.dentalclinic.admin.dto.request.RechargeTierUpsertRequest
import com.dentalclinic.admin.dto.request.SetUserTagsRequest
import com.dentalclinic.admin.dto.response.CustomerTagDto
import com.dentalclinic.admin.dto.response.MemberLevelDto
import com.dentalclinic.admin.dto.response.MessageTemplateDto
import com.dentalclinic.admin.dto.response.RechargeRecordAdminDto
import com.dentalclinic.admin.dto.response.RechargeRecordListResponse
import com.dentalclinic.admin.dto.response.RechargeTierAdminDto
import com.dentalclinic.admin.entity.Clinics
import com.dentalclinic.admin.entity.CustomerTags
import com.dentalclinic.admin.entity.MemberLevels
import com.dentalclinic.admin.entity.MessageTemplates
import com.dentalclinic.admin.entity.RechargeRecords
import com.dentalclinic.admin.entity.RechargeTiers
import com.dentalclinic.admin.entity.UserMemberLevels
import com.dentalclinic.admin.entity.UserTagRels
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.admin.util.ConflictException
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.security.PhoneCipherService
import com.dentalclinic.common.RechargeTierService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val manageDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * 充值档位后台 CRUD。
 *
 * C 端下单算赠送与档位展示读同一张 recharge_tier 表（见 common/RechargeTierService）；
 * 历史充值单的赠送额已快照在 recharge_record.bonus_amount，改配置不影响历史账。
 */
class RechargeTierAdminService : BaseService() {

    /** 全量档位（含停用），按 sortOrder 升序，后台配置页用。 */
    suspend fun listTiers(): List<RechargeTierAdminDto> = dbQuery {
        RechargeTiers.selectAll()
            .orderBy(RechargeTiers.sortOrder, SortOrder.ASC)
            .toList()
            .map { row ->
                val minAmount = row[RechargeTiers.minAmount]
                val rate = row[RechargeTiers.bonusRate]
                RechargeTierAdminDto(
                    id = row[RechargeTiers.id],
                    tierName = row[RechargeTiers.tierName],
                    minAmount = minAmount.toDouble(),
                    bonusRate = rate.toDouble(),
                    bonusAmount = RechargeTierService.bonusOf(minAmount, rate).toDouble(),
                    status = row[RechargeTiers.status],
                    sortOrder = row[RechargeTiers.sortOrder],
                    createdAt = row[RechargeTiers.createdAt].format(manageDateTimeFormatter),
                    updatedAt = row[RechargeTiers.updatedAt].format(manageDateTimeFormatter)
                )
            }
    }

    suspend fun createTier(req: RechargeTierUpsertRequest): Long = dbQuery {
        validateTier(req)
        assertMinAmountAvailable(req.minAmount.toBigDecimal())
        RechargeTiers.insert {
            it[tierName] = req.tierName.trim()
            it[minAmount] = req.minAmount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[bonusRate] = req.bonusRate.toBigDecimal().setScale(4, RoundingMode.HALF_UP)
            it[status] = req.status
            it[sortOrder] = req.sortOrder
            it[updatedAt] = LocalDateTime.now()
        } get RechargeTiers.id
    }

    suspend fun updateTier(id: Long, req: RechargeTierUpsertRequest) = dbQuery {
        if (RechargeTiers.selectAll().where { RechargeTiers.id eq id }.count() == 0L) {
            throw NotFoundException("充值档位不存在")
        }
        validateTier(req)
        assertMinAmountAvailable(req.minAmount.toBigDecimal(), excludeId = id)
        RechargeTiers.update({ RechargeTiers.id eq id }) {
            it[tierName] = req.tierName.trim()
            it[minAmount] = req.minAmount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[bonusRate] = req.bonusRate.toBigDecimal().setScale(4, RoundingMode.HALF_UP)
            it[status] = req.status
            it[sortOrder] = req.sortOrder
            it[updatedAt] = LocalDateTime.now()
        }
    }

    suspend fun deleteTier(id: Long) = dbQuery {
        if (RechargeTiers.selectAll().where { RechargeTiers.id eq id }.count() == 0L) {
            throw NotFoundException("充值档位不存在")
        }
        RechargeTiers.deleteWhere { RechargeTiers.id eq id }
    }

    private fun validateTier(req: RechargeTierUpsertRequest) {
        if (req.tierName.isBlank()) throw ValidationException("档位名称不能为空")
        if (req.minAmount <= 0) throw ValidationException("起充门槛必须大于0")
        if (req.bonusRate < 0 || req.bonusRate > 1) {
            throw ValidationException("赠送比例必须在 0~1 之间（0.1 表示赠送10%）")
        }
        if (req.status != 0 && req.status != 1) throw ValidationException("状态值非法")
    }

    /**
     * min_amount 有唯一索引（rtier_uk_min_amount），撞索引会变成 500，提前拦给可读 409。
     */
    private suspend fun assertMinAmountAvailable(minAmount: BigDecimal, excludeId: Long? = null) {
        val scaled = minAmount.setScale(2, RoundingMode.HALF_UP)
        val conflict = RechargeTiers.selectAll()
            .where { RechargeTiers.minAmount eq scaled }
            .toList()
            .firstOrNull { it[RechargeTiers.id] != excludeId }
        if (conflict != null) {
            throw ConflictException("起充门槛 ${scaled} 元的档位已存在（ID ${conflict[RechargeTiers.id]}）")
        }
    }
}

/**
 * 会员等级后台 CRUD。
 *
 * 删除保护：等级下仍有关联客户（user_member_level）时拒绝删除——
 * 删了会让这些客户的等级关联指向悬空 ID，等级复评/折扣链路都会读空。
 */
class MemberLevelAdminService : BaseService() {

    suspend fun listLevels(): List<MemberLevelDto> = dbQuery {
        val rows = MemberLevels.selectAll()
            .orderBy(MemberLevels.levelCode, SortOrder.ASC)
            .toList()
        val levelIds = rows.map { it[MemberLevels.id] }
        // 一次查询聚合各等级关联客户数，避免逐等级 count 的 N+1
        val userCountMap = if (levelIds.isNotEmpty()) {
            UserMemberLevels.selectAll()
                .where { UserMemberLevels.levelId inList levelIds }
                .toList()
                .groupingBy { it[UserMemberLevels.levelId] }
                .eachCount()
        } else emptyMap()

        rows.map { row ->
            MemberLevelDto(
                id = row[MemberLevels.id],
                levelCode = row[MemberLevels.levelCode],
                levelName = row[MemberLevels.levelName],
                minConsumption = row[MemberLevels.minConsumption].toDouble(),
                discountRate = row[MemberLevels.discountRate].toDouble(),
                benefits = row[MemberLevels.benefits],
                status = row[MemberLevels.status],
                userCount = userCountMap[row[MemberLevels.id]] ?: 0,
                createdAt = row[MemberLevels.createdAt].format(manageDateTimeFormatter)
            )
        }
    }

    suspend fun createLevel(req: MemberLevelUpsertRequest): Long = dbQuery {
        validateLevel(req)
        assertLevelCodeAvailable(req.levelCode)
        MemberLevels.insert {
            it[levelCode] = req.levelCode
            it[levelName] = req.levelName.trim()
            it[minConsumption] = req.minConsumption.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[discountRate] = req.discountRate.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[benefits] = req.benefits?.takeIf { v -> v.isNotBlank() }
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        } get MemberLevels.id
    }

    suspend fun updateLevel(id: Long, req: MemberLevelUpsertRequest) = dbQuery {
        if (MemberLevels.selectAll().where { MemberLevels.id eq id }.count() == 0L) {
            throw NotFoundException("会员等级不存在")
        }
        validateLevel(req)
        assertLevelCodeAvailable(req.levelCode, excludeId = id)
        MemberLevels.update({ MemberLevels.id eq id }) {
            it[levelCode] = req.levelCode
            it[levelName] = req.levelName.trim()
            it[minConsumption] = req.minConsumption.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[discountRate] = req.discountRate.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            it[benefits] = req.benefits?.takeIf { v -> v.isNotBlank() }
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        }
    }

    suspend fun deleteLevel(id: Long) = dbQuery {
        if (MemberLevels.selectAll().where { MemberLevels.id eq id }.count() == 0L) {
            throw NotFoundException("会员等级不存在")
        }
        val linked = UserMemberLevels.selectAll()
            .where { UserMemberLevels.levelId eq id }
            .count()
        if (linked > 0) {
            throw ConflictException("该等级下仍有 $linked 位客户，请先调整这些客户的等级后再删除")
        }
        MemberLevels.deleteWhere { MemberLevels.id eq id }
    }

    private fun validateLevel(req: MemberLevelUpsertRequest) {
        if (req.levelCode <= 0) throw ValidationException("等级编码必须大于0")
        if (req.levelName.isBlank()) throw ValidationException("等级名称不能为空")
        if (req.minConsumption < 0) throw ValidationException("升级门槛不能为负数")
        if (req.discountRate <= 0 || req.discountRate > 1) {
            throw ValidationException("折扣比例必须在 0~1 之间（0.95 表示95折，1 表示不打折）")
        }
        if (req.status != 0 && req.status != 1) throw ValidationException("状态值非法")
    }

    /** level_code 唯一索引 mlevel_uk_level_code，提前拦冲突给可读 409。 */
    private suspend fun assertLevelCodeAvailable(levelCode: Int, excludeId: Long? = null) {
        val conflict = MemberLevels.selectAll()
            .where { MemberLevels.levelCode eq levelCode }
            .toList()
            .firstOrNull { it[MemberLevels.id] != excludeId }
        if (conflict != null) {
            throw ConflictException("等级编码 $levelCode 已被「${conflict[MemberLevels.levelName]}」占用")
        }
    }
}

/**
 * 充值流水查询（管理端）。
 *
 * 数据隔离：按 recharge_record.clinic_id 收敛到账号授权门店（与消费流水一致），
 * scope 为空直接返回空页，避免无意义扫描与 SQL IN ()。
 */
class RechargeRecordAdminService : BaseService() {

    suspend fun listRecords(
        keyword: String?,
        clinicId: Long?,
        status: String?,
        dateFrom: String?,
        dateTo: String?,
        page: Int,
        pageSize: Int,
        scope: ClinicScope
    ): RechargeRecordListResponse = dbQuery {
        if (scope.isEmpty) {
            return@dbQuery RechargeRecordListResponse(emptyList(), 0, page, pageSize)
        }

        var query = RechargeRecords.selectAll()

        // 客户关键字（手机/姓名/昵称）：先定位 userId 集合再下推；无命中直接空页
        if (!keyword.isNullOrBlank()) {
            val pattern = "%${keyword.trim().lowercase()}%"
            val nameCondition = (Users.nickname.lowerCase() like pattern) or
                (Users.realName.lowerCase() like pattern)
            val keywordCondition = if (PhoneCipherService.isValidMainlandMobile(keyword)) {
                (Users.phoneHash eq PhoneCipherService.blindIndex(keyword)) or nameCondition
            } else {
                nameCondition
            }
            val matchedUserIds = Users.selectAll()
                .where { keywordCondition }
                .toList()
                .map { it[Users.id] }
            if (matchedUserIds.isEmpty()) {
                return@dbQuery RechargeRecordListResponse(emptyList(), 0, page, pageSize)
            }
            query = query.andWhere { RechargeRecords.userId inList matchedUserIds }
        }

        if (clinicId != null) {
            query = query.andWhere { RechargeRecords.clinicId eq clinicId }
        }
        if (!scope.all) {
            query = query.andWhere { RechargeRecords.clinicId inList scope.clinicIds }
        }
        if (!status.isNullOrBlank()) {
            query = query.andWhere { RechargeRecords.status eq status }
        }
        if (dateFrom != null) {
            query = query.andWhere { RechargeRecords.createdAt greaterEq parseDateBoundary(dateFrom, endOfDay = false) }
        }
        if (dateTo != null) {
            query = query.andWhere { RechargeRecords.createdAt lessEq parseDateBoundary(dateTo, endOfDay = true) }
        }

        val total = query.count()
        val rows = query
            .orderBy(RechargeRecords.createdAt, SortOrder.DESC)
            .limit(pageSize).offset(((page - 1) * pageSize).toLong())
            .toList()

        val userIds = rows.map { it[RechargeRecords.userId] }.distinct()
        val clinicIds = rows.map { it[RechargeRecords.clinicId] }.distinct()
        val usersMap = if (userIds.isNotEmpty()) {
            Users.selectAll().where { Users.id inList userIds }.toList()
                .associateBy { it[Users.id] }
        } else emptyMap()
        val clinicsMap = if (clinicIds.isNotEmpty()) {
            Clinics.selectAll().where { Clinics.id inList clinicIds }.toList()
                .associateBy { it[Clinics.id] }
        } else emptyMap()

        val list = rows.map { row ->
            val user = usersMap[row[RechargeRecords.userId]]
            RechargeRecordAdminDto(
                id = row[RechargeRecords.id],
                userId = row[RechargeRecords.userId],
                userName = user?.get(Users.realName) ?: user?.get(Users.nickname),
                userPhone = PhoneCipherService.readPhone(user?.get(Users.phoneCipher), user?.get(Users.phone)),
                clinicId = row[RechargeRecords.clinicId],
                clinicName = clinicsMap[row[RechargeRecords.clinicId]]?.get(Clinics.name) ?: "",
                amount = row[RechargeRecords.amount].toDouble(),
                bonusAmount = row[RechargeRecords.bonusAmount].toDouble(),
                paymentMethod = row[RechargeRecords.paymentMethod],
                wechatOrderId = row[RechargeRecords.wechatOrderId],
                outTradeNo = row[RechargeRecords.outTradeNo],
                status = row[RechargeRecords.status],
                paidAt = row[RechargeRecords.paidAt]?.format(manageDateTimeFormatter),
                createdAt = row[RechargeRecords.createdAt].format(manageDateTimeFormatter)
            )
        }

        RechargeRecordListResponse(list = list, total = total, page = page, pageSize = pageSize)
    }

    private fun parseDateBoundary(date: String, endOfDay: Boolean): LocalDateTime =
        try {
            LocalDateTime.parse(date + if (endOfDay) "T23:59:59" else "T00:00:00")
        } catch (e: DateTimeParseException) {
            throw ValidationException("日期格式应为 yyyy-MM-dd，收到：$date")
        }
}

/**
 * 客户标签字典与客户打标。
 *
 * setUserTags 为全量覆盖语义（同账号角色/门店授权的写法）：先清空再批量写入，
 * 前端每次提交该客户的完整标签集合即可，无需自己算增删差集。
 */
class CustomerTagAdminService : BaseService() {

    suspend fun listTags(): List<CustomerTagDto> = dbQuery {
        CustomerTags.selectAll()
            .orderBy(CustomerTags.id, SortOrder.ASC)
            .toList()
            .map { it.toTagDto() }
    }

    suspend fun createTag(req: CustomerTagUpsertRequest): Long = dbQuery {
        validateTag(req)
        assertTagNameAvailable(req.tagName)
        CustomerTags.insert {
            it[tagName] = req.tagName.trim()
            it[color] = req.color.trim().ifBlank { "blue" }
            it[remark] = req.remark?.takeIf { v -> v.isNotBlank() }
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        } get CustomerTags.id
    }

    suspend fun updateTag(id: Long, req: CustomerTagUpsertRequest) = dbQuery {
        if (CustomerTags.selectAll().where { CustomerTags.id eq id }.count() == 0L) {
            throw NotFoundException("客户标签不存在")
        }
        validateTag(req)
        assertTagNameAvailable(req.tagName, excludeId = id)
        CustomerTags.update({ CustomerTags.id eq id }) {
            it[tagName] = req.tagName.trim()
            it[color] = req.color.trim().ifBlank { "blue" }
            it[remark] = req.remark?.takeIf { v -> v.isNotBlank() }
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        }
    }

    /** 删除标签时同步清理客户-标签关系，否则留下悬空关系污染客户标签查询。 */
    suspend fun deleteTag(id: Long) = dbQuery {
        if (CustomerTags.selectAll().where { CustomerTags.id eq id }.count() == 0L) {
            throw NotFoundException("客户标签不存在")
        }
        UserTagRels.deleteWhere { UserTagRels.tagId eq id }
        CustomerTags.deleteWhere { CustomerTags.id eq id }
    }

    /** 查询某客户当前的标签集合（客户详情/打标弹窗回显用）。 */
    suspend fun getUserTags(userId: Long, scope: ClinicScope): List<CustomerTagDto> = dbQuery {
        requireUserInScope(userId, scope)
        val tagIds = UserTagRels.selectAll()
            .where { UserTagRels.userId eq userId }
            .toList()
            .map { it[UserTagRels.tagId] }
        if (tagIds.isEmpty()) return@dbQuery emptyList()
        CustomerTags.selectAll()
            .where { CustomerTags.id inList tagIds }
            .orderBy(CustomerTags.id, SortOrder.ASC)
            .toList()
            .map { it.toTagDto() }
    }

    /**
     * 全量覆盖客户标签。
     *
     * @throws ValidationException 客户不存在或提交了不存在/已停用的标签
     */
    suspend fun setUserTags(userId: Long, req: SetUserTagsRequest, scope: ClinicScope) = dbQuery {
        requireUserInScope(userId, scope)
        val distinctTagIds = req.tagIds.distinct()
        if (distinctTagIds.isNotEmpty()) {
            val validRows = CustomerTags.selectAll()
                .where { (CustomerTags.id inList distinctTagIds) and (CustomerTags.status eq 1) }
                .toList()
            if (validRows.size != distinctTagIds.size) {
                throw ValidationException("包含无效或已停用的标签，请刷新后重试")
            }
        }
        UserTagRels.deleteWhere { UserTagRels.userId eq userId }
        if (distinctTagIds.isNotEmpty()) {
            UserTagRels.batchInsert(distinctTagIds, body = { tagId ->
                this[UserTagRels.userId] = userId
                this[UserTagRels.tagId] = tagId
            })
        }
    }

    private fun validateTag(req: CustomerTagUpsertRequest) {
        if (req.tagName.isBlank()) throw ValidationException("标签名称不能为空")
        if (req.status != 0 && req.status != 1) throw ValidationException("状态值非法")
    }

    /** tag_name 唯一索引 ctag_uk_tag_name，提前拦冲突给可读 409。 */
    private suspend fun assertTagNameAvailable(tagName: String, excludeId: Long? = null) {
        val name = tagName.trim()
        val conflict = CustomerTags.selectAll()
            .where { CustomerTags.tagName eq name }
            .toList()
            .firstOrNull { it[CustomerTags.id] != excludeId }
        if (conflict != null) {
            throw ConflictException("标签「$name」已存在")
        }
    }

    private fun ResultRow.toTagDto(): CustomerTagDto = CustomerTagDto(
        id = this[CustomerTags.id],
        tagName = this[CustomerTags.tagName],
        color = this[CustomerTags.color],
        remark = this[CustomerTags.remark],
        status = this[CustomerTags.status]
    )
}

/**
 * 微信订阅消息模板后台 CRUD。
 *
 * 内置 3 个模板（预约前一天/当天提醒、积分到期提醒）被定时任务按固定 key 读取，
 * 只可编辑不可删除、key 不可改；运营可新增自定义模板。
 */
class MessageTemplateAdminService : BaseService() {

    suspend fun listTemplates(): List<MessageTemplateDto> = dbQuery {
        MessageTemplates.selectAll()
            .orderBy(MessageTemplates.id, SortOrder.ASC)
            .toList()
            .map { row ->
                val key = row[MessageTemplates.templateKey]
                MessageTemplateDto(
                    id = row[MessageTemplates.id],
                    templateKey = key,
                    templateId = row[MessageTemplates.templateId],
                    title = row[MessageTemplates.title],
                    content = row[MessageTemplates.content],
                    triggerEvent = row[MessageTemplates.triggerEvent],
                    status = row[MessageTemplates.status],
                    builtIn = key in BUILTIN_KEYS,
                    updatedAt = row[MessageTemplates.updatedAt].format(manageDateTimeFormatter)
                )
            }
    }

    suspend fun createTemplate(req: MessageTemplateUpsertRequest): Long = dbQuery {
        validateTemplate(req)
        val key = req.templateKey.trim()
        if (MessageTemplates.selectAll().where { MessageTemplates.templateKey eq key }.count() > 0) {
            throw ConflictException("模板标识「$key」已存在")
        }
        MessageTemplates.insert {
            it[templateKey] = key
            it[templateId] = req.templateId.trim()
            it[title] = req.title.trim()
            it[content] = req.content
            it[triggerEvent] = req.triggerEvent?.takeIf { v -> v.isNotBlank() } ?: key
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        } get MessageTemplates.id
    }

    suspend fun updateTemplate(id: Long, req: MessageTemplateUpsertRequest) = dbQuery {
        val existing = MessageTemplates.selectAll()
            .where { MessageTemplates.id eq id }
            .toList()
            .firstOrNull() ?: throw NotFoundException("消息模板不存在")
        validateTemplate(req)
        val existingKey = existing[MessageTemplates.templateKey]
        // 内置模板被定时任务按固定 key 读取，改 key 等于静默关掉一类通知
        if (existingKey in BUILTIN_KEYS && req.templateKey.trim() != existingKey) {
            throw ConflictException("内置模板的标识不可修改")
        }
        val key = req.templateKey.trim()
        val keyConflict = MessageTemplates.selectAll()
            .where { MessageTemplates.templateKey eq key }
            .toList()
            .firstOrNull { it[MessageTemplates.id] != id }
        if (keyConflict != null) throw ConflictException("模板标识「$key」已存在")

        MessageTemplates.update({ MessageTemplates.id eq id }) {
            it[templateKey] = key
            it[templateId] = req.templateId.trim()
            it[title] = req.title.trim()
            it[content] = req.content
            it[triggerEvent] = req.triggerEvent?.takeIf { v -> v.isNotBlank() } ?: key
            it[status] = req.status
            it[updatedAt] = LocalDateTime.now()
        }
    }

    suspend fun deleteTemplate(id: Long) = dbQuery {
        val row = MessageTemplates.selectAll()
            .where { MessageTemplates.id eq id }
            .toList()
            .firstOrNull() ?: throw NotFoundException("消息模板不存在")
        if (row[MessageTemplates.templateKey] in BUILTIN_KEYS) {
            throw ConflictException("内置消息模板不可删除，可停用或编辑内容")
        }
        MessageTemplates.deleteWhere { MessageTemplates.id eq id }
    }

    private fun validateTemplate(req: MessageTemplateUpsertRequest) {
        if (req.templateKey.isBlank()) throw ValidationException("模板标识不能为空")
        if (req.title.isBlank()) throw ValidationException("模板标题不能为空")
        if (req.content.isBlank()) throw ValidationException("模板内容不能为空")
        if (req.status != 0 && req.status != 1) throw ValidationException("状态值非法")
    }

    companion object {
        /** 定时任务按固定 key 读取的内置模板，与 DataSeedService 的播种保持一致。 */
        val BUILTIN_KEYS: Set<String> = setOf(
            "appointment_remind_evening",
            "appointment_remind_morning",
            "points_expire_remind"
        )
    }
}
