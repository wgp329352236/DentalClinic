package com.dentalclinic.admin.service

import com.dentalclinic.admin.entity.ContentLinkType
import com.dentalclinic.admin.entity.MpBanners
import com.dentalclinic.admin.entity.MpNotices
import com.dentalclinic.admin.util.NotFoundException
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime

/**
 * 小程序内容配置管理服务（需求 4.11：首页轮播图、公告内容维护）。
 */
class AdminContentService : BaseService() {

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
        query
            .orderBy(MpBanners.sortOrder to SortOrder.ASC, MpBanners.id to SortOrder.DESC)
            .toList()
            .map { row ->
                BannerItemDto(
                    id = row[MpBanners.id],
                    title = row[MpBanners.title],
                    imageUrl = row[MpBanners.imageUrl],
                    linkType = row[MpBanners.linkType],
                    linkTarget = row[MpBanners.linkTarget],
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
        MpBanners.insert {
            it[MpBanners.title] = request.title.trim()
            it[MpBanners.imageUrl] = request.imageUrl.trim()
            it[MpBanners.linkType] = request.linkType
            it[MpBanners.linkTarget] = normalizeLinkTarget(request)
            it[MpBanners.sortOrder] = request.sortOrder
            it[MpBanners.status] = request.status
            it[MpBanners.startAt] = parseDate(request.startAt, endOfDay = false)
            it[MpBanners.endAt] = parseDate(request.endAt, endOfDay = true)
            it[MpBanners.createdBy] = operator
        } get MpBanners.id
    }

    suspend fun updateBanner(id: Long, request: SaveBannerRequest) {
        validateBanner(request)
        val updated = dbQuery {
            MpBanners.update({ MpBanners.id eq id }) {
                it[MpBanners.title] = request.title.trim()
                it[MpBanners.imageUrl] = request.imageUrl.trim()
                it[MpBanners.linkType] = request.linkType
                it[MpBanners.linkTarget] = normalizeLinkTarget(request)
                it[MpBanners.sortOrder] = request.sortOrder
                it[MpBanners.status] = request.status
                it[MpBanners.startAt] = parseDate(request.startAt, endOfDay = false)
                it[MpBanners.endAt] = parseDate(request.endAt, endOfDay = true)
                it[updatedAt] = LocalDateTime.now()
            }
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
        validateCommon(request.linkType, request.linkTarget, request.startAt, request.endAt)
    }

    private fun validateNotice(request: SaveNoticeRequest) {
        if (request.content.isBlank()) throw ValidationException("公告内容不能为空")
        validateCommon(request.linkType, request.linkTarget, request.startAt, request.endAt)
    }

    private fun validateCommon(linkType: String, linkTarget: String?, startAt: String?, endAt: String?) {
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
        val start = parseDate(startAt, endOfDay = false)
        val end = parseDate(endAt, endOfDay = true)
        if (start != null && end != null && end < start) {
            throw ValidationException("生效截止日期不能早于起始日期")
        }
    }

    private fun normalizeLinkTarget(request: SaveBannerRequest): String? =
        normalizeLinkTarget(request.linkType, request.linkTarget)

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
data class SaveBannerRequest(
    val title: String,
    val imageUrl: String,
    val linkType: String = ContentLinkType.NONE,
    val linkTarget: String? = null,
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
    val linkType: String,
    val linkTarget: String?,
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
