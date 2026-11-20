package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.ContentLinkType
import com.dentalclinic.admin.entity.MpActivities
import com.dentalclinic.admin.entity.MpBanners
import com.dentalclinic.admin.entity.MpNotices
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.scheduler.ScheduledNotifier
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime

/**
 * 小程序内容配置管理服务（需求 4.11：首页轮播图、公告内容维护）。
 *
 * 轮播图与活动严格一对一：每张轮播图必须绑定一个活动（activity_id 唯一），
 * 用户点击轮播图进入活动详情页；活动本身也在这里维护，避免跨服务的事务与校验分裂。
 */
class AdminContentService : BaseService() {

    // ==================== 活动 ====================

    /**
     * 活动列表。管理端看全量（含停用与未开始/已结束的），排期过滤只发生在 C 端。
     * bannerId 回带当前绑定的轮播图，便于前端直接展示「是否已配置轮播图」。
     */
    suspend fun listActivities(status: Int?): List<ActivityItemDto> = dbQuery {
        var query = MpActivities.selectAll()
        if (status != null) {
            query = query.andWhere { MpActivities.status eq status }
        }
        val activities = query
            .orderBy(MpActivities.sortOrder to SortOrder.ASC, MpActivities.id to SortOrder.DESC)
            .toList()
        val bannerByActivity = MpBanners.selectAll()
            .where { MpBanners.activityId.isNotNull() }
            .toList()
            .associate { it[MpBanners.activityId] to it[MpBanners.id] }

        activities.map { row ->
            ActivityItemDto(
                id = row[MpActivities.id],
                title = row[MpActivities.title],
                coverImage = row[MpActivities.coverImage],
                summary = row[MpActivities.summary],
                content = row[MpActivities.content],
                sortOrder = row[MpActivities.sortOrder],
                status = row[MpActivities.status],
                startAt = row[MpActivities.startAt]?.format(DISPLAY_FORMATTER),
                endAt = row[MpActivities.endAt]?.format(DISPLAY_FORMATTER),
                bannerId = bannerByActivity[row[MpActivities.id]],
                createdBy = row[MpActivities.createdBy],
                updatedAt = row[MpActivities.updatedAt].format(DISPLAY_FORMATTER)
            )
        }
    }

    suspend fun createActivity(request: SaveActivityRequest, operator: String): Long {
        validateActivity(request)
        val activityId = dbQuery {
            MpActivities.insert {
                it[MpActivities.title] = request.title.trim()
                it[MpActivities.coverImage] = request.coverImage?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.summary] = request.summary?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.content] = request.content?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.sortOrder] = request.sortOrder
                it[MpActivities.status] = request.status
                it[MpActivities.startAt] = parseDate(request.startAt, endOfDay = false)
                it[MpActivities.endAt] = parseDate(request.endAt, endOfDay = true)
                it[MpActivities.createdBy] = operator
            } get MpActivities.id
        }
        if (request.status == 1) {
            dispatchActivityNotice(activityId, request.title.trim(), request.summary, request.content)
        }
        return activityId
    }

    suspend fun updateActivity(id: Long, request: SaveActivityRequest) {
        validateActivity(request)
        val shouldNotify = dbQuery {
            val current = MpActivities.selectAll()
                .where { MpActivities.id eq id }
                .toList()
                .singleOrNull()
                ?: throw NotFoundException("活动不存在")
            MpActivities.update({ MpActivities.id eq id }) {
                it[MpActivities.title] = request.title.trim()
                it[MpActivities.coverImage] = request.coverImage?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.summary] = request.summary?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.content] = request.content?.trim()?.takeIf(String::isNotEmpty)
                it[MpActivities.sortOrder] = request.sortOrder
                it[MpActivities.status] = request.status
                it[MpActivities.startAt] = parseDate(request.startAt, endOfDay = false)
                it[MpActivities.endAt] = parseDate(request.endAt, endOfDay = true)
                it[updatedAt] = LocalDateTime.now()
            }
            // 只在「停用/草稿 → 上线」时群发，编辑已上线活动不重复打扰用户。
            current[MpActivities.status] != 1 && request.status == 1
        }
        if (shouldNotify) {
            dispatchActivityNotice(id, request.title.trim(), request.summary, request.content)
        }
    }

    /**
     * 活动上线通知：站内信必达，微信订阅消息尽力发送。
     *
     * 放到后台执行，通知模板缺失、用户未授权或微信网络异常都不能影响活动保存事务。
     */
    private fun dispatchActivityNotice(
        activityId: Long,
        title: String,
        summary: String?,
        content: String?
    ) {
        val detail = content?.trim()?.takeIf(String::isNotEmpty)
            ?: summary?.trim()?.takeIf(String::isNotEmpty)
            ?: "点击查看活动详情"
        val notificationContent = buildString {
            append("活动名称：").append(title)
            append("\n活动内容：").append(detail)
        }
        val wechatSummary = (summary?.trim()?.takeIf(String::isNotEmpty) ?: detail)
            .let { if (it.length <= 20) it else it.take(19) + "…" }
        ScheduledNotifier.notifyActiveUsersAsync(
            templateKey = "activity_notice",
            title = "活动通知：$title",
            content = notificationContent,
            subscribeData = mapOf(
                "thing1" to title.let { if (it.length <= 20) it else it.take(19) + "…" },
                "thing2" to wechatSummary
            ),
            page = "/pages/activity/activity?id=$activityId",
            msgType = "activity",
            relatedId = activityId
        )
    }

    /** 已绑定轮播图的活动不能删：轮播图会指向空活动，C 端点击必然 404。 */
    suspend fun deleteActivity(id: Long) = dbQuery {
        val exists = MpActivities.selectAll()
            .where { MpActivities.id eq id }
            .toList()
            .isNotEmpty()
        if (!exists) throw NotFoundException("活动不存在")

        val bound = MpBanners.selectAll()
            .where { MpBanners.activityId eq id }
            .toList()
        if (bound.isNotEmpty()) {
            throw ValidationException("该活动已绑定轮播图（ID ${bound.first()[MpBanners.id]}），请先删除或改绑轮播图")
        }
        MpActivities.deleteWhere { MpActivities.id eq id }
    }

    // ==================== 轮播图 ====================

    /**
     * 轮播图列表。管理端要看全量（含停用与未生效的），排期过滤只发生在 C 端读取接口。
     *
     * @param status 传 null 表示不过滤
     */
    suspend fun listBanners(status: Int?): List<BannerItemDto> = dbQuery {
        var query = MpBanners.selectAll()
        if (status != null) {
            query = query.andWhere { MpBanners.status eq status }
        }
        val rows = query
            .orderBy(MpBanners.sortOrder to SortOrder.ASC, MpBanners.id to SortOrder.DESC)
            .toList()
        val activityTitles = activityTitlesOf(rows.mapNotNull { it[MpBanners.activityId] })

        rows.map { row ->
            BannerItemDto(
                id = row[MpBanners.id],
                title = row[MpBanners.title],
                imageUrl = row[MpBanners.imageUrl],
                activityId = row[MpBanners.activityId],
                activityTitle = row[MpBanners.activityId]?.let(activityTitles::get),
                sortOrder = row[MpBanners.sortOrder],
                status = row[MpBanners.status],
                startAt = row[MpBanners.startAt]?.format(DISPLAY_FORMATTER),
                endAt = row[MpBanners.endAt]?.format(DISPLAY_FORMATTER),
                createdBy = row[MpBanners.createdBy],
                updatedAt = row[MpBanners.updatedAt].format(DISPLAY_FORMATTER)
            )
        }
    }

    suspend fun createBanner(request: SaveBannerRequest, operator: String): Long = dbQuery {
        validateBanner(request)
        ensureActivityAvailable(request.activityId, selfBannerId = null)
        MpBanners.insert {
            it[MpBanners.title] = request.title.trim()
            it[MpBanners.imageUrl] = request.imageUrl.trim()
            it[MpBanners.activityId] = request.activityId
            it[MpBanners.linkType] = ContentLinkType.NONE
            it[MpBanners.linkTarget] = null
            it[MpBanners.sortOrder] = request.sortOrder
            it[MpBanners.status] = request.status
            it[MpBanners.startAt] = parseDate(request.startAt, endOfDay = false)
            it[MpBanners.endAt] = parseDate(request.endAt, endOfDay = true)
            it[MpBanners.createdBy] = operator
        } get MpBanners.id
    }

    suspend fun updateBanner(id: Long, request: SaveBannerRequest) = dbQuery {
        validateBanner(request)
        ensureActivityAvailable(request.activityId, selfBannerId = id)
        val updated = MpBanners.update({ MpBanners.id eq id }) {
            it[MpBanners.title] = request.title.trim()
            it[MpBanners.imageUrl] = request.imageUrl.trim()
            it[MpBanners.activityId] = request.activityId
            it[MpBanners.linkType] = ContentLinkType.NONE
            it[MpBanners.linkTarget] = null
            it[MpBanners.sortOrder] = request.sortOrder
            it[MpBanners.status] = request.status
            it[MpBanners.startAt] = parseDate(request.startAt, endOfDay = false)
            it[MpBanners.endAt] = parseDate(request.endAt, endOfDay = true)
            it[updatedAt] = LocalDateTime.now()
        }
        if (updated == 0) throw NotFoundException("轮播图不存在")
    }

    suspend fun deleteBanner(id: Long) = dbQuery {
        // 先查再删：与 AdminCouponService 一致，不依赖 deleteWhere 的返回计数就能给出准确的 404
        val exists = MpBanners.selectAll()
            .where { MpBanners.id eq id }
            .toList()
            .isNotEmpty()
        if (!exists) throw NotFoundException("轮播图不存在")
        MpBanners.deleteWhere { MpBanners.id eq id }
    }

    // ==================== 公告 ====================

    suspend fun listNotices(status: Int?): List<NoticeItemDto> = dbQuery {
        var query = MpNotices.selectAll()
        if (status != null) {
            query = query.andWhere { MpNotices.status eq status }
        }
        query
            .orderBy(MpNotices.sortOrder to SortOrder.ASC, MpNotices.id to SortOrder.DESC)
            .toList()
            .map { row ->
                NoticeItemDto(
                    id = row[MpNotices.id],
                    content = row[MpNotices.content],
                    linkType = row[MpNotices.linkType],
                    linkTarget = row[MpNotices.linkTarget],
                    sortOrder = row[MpNotices.sortOrder],
                    status = row[MpNotices.status],
                    startAt = row[MpNotices.startAt]?.format(DISPLAY_FORMATTER),
                    endAt = row[MpNotices.endAt]?.format(DISPLAY_FORMATTER),
                    createdBy = row[MpNotices.createdBy],
                    updatedAt = row[MpNotices.updatedAt].format(DISPLAY_FORMATTER)
                )
            }
    }

    suspend fun createNotice(request: SaveNoticeRequest, operator: String): Long = dbQuery {
        validateNotice(request)
        MpNotices.insert {
            it[MpNotices.content] = request.content.trim()
            it[MpNotices.linkType] = request.linkType
            it[MpNotices.linkTarget] = normalizeLinkTarget(request.linkType, request.linkTarget)
            it[MpNotices.sortOrder] = request.sortOrder
            it[MpNotices.status] = request.status
            it[MpNotices.startAt] = parseDate(request.startAt, endOfDay = false)
            it[MpNotices.endAt] = parseDate(request.endAt, endOfDay = true)
            it[MpNotices.createdBy] = operator
        } get MpNotices.id
    }

    suspend fun updateNotice(id: Long, request: SaveNoticeRequest) {
        validateNotice(request)
        val updated = dbQuery {
            MpNotices.update({ MpNotices.id eq id }) {
                it[MpNotices.content] = request.content.trim()
                it[MpNotices.linkType] = request.linkType
                it[MpNotices.linkTarget] = normalizeLinkTarget(request.linkType, request.linkTarget)
                it[MpNotices.sortOrder] = request.sortOrder
                it[MpNotices.status] = request.status
                it[MpNotices.startAt] = parseDate(request.startAt, endOfDay = false)
                it[MpNotices.endAt] = parseDate(request.endAt, endOfDay = true)
                it[updatedAt] = LocalDateTime.now()
            }
        }
        if (updated == 0) throw NotFoundException("公告不存在")
    }

    suspend fun deleteNotice(id: Long) = dbQuery {
        val exists = MpNotices.selectAll()
            .where { MpNotices.id eq id }
            .toList()
            .isNotEmpty()
        if (!exists) throw NotFoundException("公告不存在")
        MpNotices.deleteWhere { MpNotices.id eq id }
    }

    // ==================== 校验与解析 ====================

    private fun validateBanner(request: SaveBannerRequest) {
        if (request.title.isBlank()) throw ValidationException("标题不能为空")
        if (request.imageUrl.isBlank()) throw ValidationException("图片地址不能为空")
        validateDateRange(request.startAt, request.endAt)
    }

    private fun validateActivity(request: SaveActivityRequest) {
        if (request.title.isBlank()) throw ValidationException("活动名称不能为空")
        if (request.summary != null && request.summary.length > 500) {
            throw ValidationException("活动简介不能超过 500 字")
        }
        validateDateRange(request.startAt, request.endAt)
    }

    /**
     * 活动必须存在且未被其他轮播图占用。
     *
     * @param selfBannerId 编辑场景传当前轮播图 ID，避免把自己判成「占用者」
     */
    private suspend fun ensureActivityAvailable(activityId: Long?, selfBannerId: Long?) {
        if (activityId == null) throw ValidationException("请选择轮播图对应的活动")
        val activityExists = MpActivities.selectAll()
            .where { MpActivities.id eq activityId }
            .toList()
            .isNotEmpty()
        if (!activityExists) throw ValidationException("活动不存在或已被删除")

        val occupied = MpBanners.selectAll()
            .where { MpBanners.activityId eq activityId }
            .toList()
            .firstOrNull { it[MpBanners.id] != selfBannerId }
            ?: return
        throw ValidationException("该活动已绑定轮播图（ID ${occupied[MpBanners.id]}），一个活动只能对应一张轮播图")
    }

    /** 批量取活动名称，避免列表页 N+1 查询。 */
    private suspend fun activityTitlesOf(activityIds: List<Long>): Map<Long, String> {
        if (activityIds.isEmpty()) return emptyMap()
        return MpActivities.selectAll()
            .where { MpActivities.id inList activityIds }
            .toList()
            .associate { it[MpActivities.id] to it[MpActivities.title] }
    }

    private fun validateNotice(request: SaveNoticeRequest) {
        if (request.content.isBlank()) throw ValidationException("公告内容不能为空")
        validateCommon(request.linkType, request.linkTarget)
        validateDateRange(request.startAt, request.endAt)
    }

    private fun validateCommon(linkType: String, linkTarget: String?) {
        if (linkType !in ContentLinkType.ALL) {
            throw ValidationException("跳转类型无效，应为 ${ContentLinkType.ALL.joinToString("/")}")
        }
        // 声明了跳转却没填目标，C 端点击会落到空路径上静默失败，必须在录入时就拦住
        if (linkType != ContentLinkType.NONE && linkTarget.isNullOrBlank()) {
            throw ValidationException("跳转类型为 $linkType 时必须填写跳转目标")
        }
        if (linkType == ContentLinkType.WEBVIEW && !linkTarget.isNullOrBlank() &&
            !linkTarget.startsWith("https://")
        ) {
            // 小程序 web-view 只允许 https 且需在公众平台配置业务域名，http 链接注定打不开
            throw ValidationException("外部网页跳转目标必须是 https 地址")
        }
    }

    private fun validateDateRange(startAt: String?, endAt: String?) {
        val start = parseDate(startAt, endOfDay = false)
        val end = parseDate(endAt, endOfDay = true)
        if (start != null && end != null && end < start) {
            throw ValidationException("截止日期不能早于起始日期")
        }
    }

    /** 不跳转时强制清空目标，避免残留脏值在后续改成可跳转时被误用 */
    private fun normalizeLinkTarget(linkType: String, linkTarget: String?): String? =
        if (linkType == ContentLinkType.NONE) null else linkTarget?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 解析 yyyy-MM-dd 形式的日期串，null/空白表示不限排期。
     *
     * 与 AdminCouponService 的 validFrom/validTo 保持同一口径：起始日取 00:00:00、
     * 截止日取 23:59:59，使「截止到某天」在业务上包含当天全天。
     * 同时容忍前端直接传完整的 yyyy-MM-dd HH:mm:ss。
     */
    private fun parseDate(value: String?, endOfDay: Boolean): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        val date = value.trim()
        return try {
            if (date.length > 10) {
                LocalDateTime.parse(date.replace(' ', 'T'))
            } else {
                LocalDateTime.parse(date + if (endOfDay) "T23:59:59" else "T00:00:00")
            }
        } catch (e: Exception) {
            throw ValidationException("日期格式无效，应为 yyyy-MM-dd")
        }
    }

    companion object {
        private val DISPLAY_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

// ==================== DTO ====================

@Serializable
data class SaveActivityRequest(
    val title: String,
    val coverImage: String? = null,
    val summary: String? = null,
    /** 活动详情，纯文本，小程序按换行分段渲染 */
    val content: String? = null,
    val sortOrder: Int = 0,
    val status: Int = 1,
    /** yyyy-MM-dd，null 表示不限 */
    val startAt: String? = null,
    val endAt: String? = null
)

@Serializable
data class ActivityItemDto(
    val id: Long,
    val title: String,
    val coverImage: String?,
    val summary: String?,
    val content: String?,
    val sortOrder: Int,
    val status: Int,
    val startAt: String?,
    val endAt: String?,
    /** 已绑定的轮播图 ID，null 表示还没配轮播图 */
    val bannerId: Long?,
    val createdBy: String?,
    val updatedAt: String
)

@Serializable
data class SaveBannerRequest(
    val title: String,
    val imageUrl: String,
    /** 关联活动（必填，一一对应） */
    val activityId: Long? = null,
    val sortOrder: Int = 0,
    val status: Int = 1,
    /** yyyy-MM-dd，null 表示不限 */
    val startAt: String? = null,
    val endAt: String? = null
)

@Serializable
data class SaveNoticeRequest(
    val content: String,
    val linkType: String = ContentLinkType.NONE,
    val linkTarget: String? = null,
    val sortOrder: Int = 0,
    val status: Int = 1,
    val startAt: String? = null,
    val endAt: String? = null
)

@Serializable
data class BannerItemDto(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val activityId: Long?,
    val activityTitle: String?,
    val sortOrder: Int,
    val status: Int,
    val startAt: String?,
    val endAt: String?,
    val createdBy: String?,
    val updatedAt: String
)

@Serializable
data class NoticeItemDto(
    val id: Long,
    val content: String,
    val linkType: String,
    val linkTarget: String?,
    val sortOrder: Int,
    val status: Int,
    val startAt: String?,
    val endAt: String?,
    val createdBy: String?,
    val updatedAt: String
)
