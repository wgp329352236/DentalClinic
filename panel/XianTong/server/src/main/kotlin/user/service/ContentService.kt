package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.MpBanners
import com.dentalclinic.admin.entity.MpNotices
import com.dentalclinic.admin.entity.MessageTemplates
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDateTime

/**
 * 小程序首页内容服务（需求 4.11 的读取侧）。
 *
 * 首页在未登录状态下也要展示轮播图与公告，因此本服务只被公开路由调用，不涉及用户身份。
 */
class ContentService : BaseService() {

    /**
     * 一次返回首页所需的全部可展示内容。
     *
     * 合并成单个接口而不是 banners / notices 两个：需求 5.1 要求小程序首屏加载不超过 2 秒，
     * 首页本就要并发拉门店列表与用户资料，再拆两个请求会多一轮 RTT。
     */
    suspend fun getHomeContent(): HomeContentDto = dbQuery {
        val now = LocalDateTime.now()

        // 排期过滤三条件：启用中、已到生效起始、未过生效截止。
        // startAt/endAt 为 null 表示该侧不限，用 isNull() 短路，避免 null 参与比较得到 UNKNOWN 而整条被滤掉。
        val banners = MpBanners.selectAll()
            .where {
                (MpBanners.status eq 1) and
                    (MpBanners.startAt.isNull() or (MpBanners.startAt lessEq now)) and
                    (MpBanners.endAt.isNull() or (MpBanners.endAt greaterEq now))
            }
            .orderBy(MpBanners.sortOrder to SortOrder.ASC, MpBanners.id to SortOrder.DESC)
            .limit(MAX_BANNERS)
            .toList()
            .map { row ->
                BannerDto(
                    id = row[MpBanners.id],
                    title = row[MpBanners.title],
                    imageUrl = row[MpBanners.imageUrl],
                    linkType = row[MpBanners.linkType],
                    linkTarget = row[MpBanners.linkTarget]
                )
            }

        val notices = MpNotices.selectAll()
            .where {
                (MpNotices.status eq 1) and
                    (MpNotices.startAt.isNull() or (MpNotices.startAt lessEq now)) and
                    (MpNotices.endAt.isNull() or (MpNotices.endAt greaterEq now))
            }
            .orderBy(MpNotices.sortOrder to SortOrder.ASC, MpNotices.id to SortOrder.DESC)
            .limit(MAX_NOTICES)
            .toList()
            .map { row ->
                NoticeDto(
                    id = row[MpNotices.id],
                    content = row[MpNotices.content],
                    linkType = row[MpNotices.linkType],
                    linkTarget = row[MpNotices.linkTarget]
                )
            }

        HomeContentDto(banners = banners, notices = notices)
    }

    /**
     * 微信订阅消息模板配置（公开接口，预约成功后小程序据此申请订阅授权）。
     *
     * 只返回「启用中且已填真实微信模板 ID」的模板：DataSeedService 播种的模板 templateId
     * 为空串占位，此时不返回，小程序侧拿不到 ID 就不弹授权框（wx.requestSubscribeMessage
     * 传空/非法 ID 会直接报错），提醒全部走站内信；管理端在通知模板配置里填入真实 ID 后
     * 无需发版即可恢复订阅授权弹窗。
     */
    suspend fun getSubscribeTemplates(): List<SubscribeTemplateDto> = dbQuery {
        MessageTemplates.selectAll()
            .where { MessageTemplates.status eq 1 }
            .toList()
            .filter { it[MessageTemplates.templateId].isNotBlank() }
            .map { row ->
                SubscribeTemplateDto(
                    templateKey = row[MessageTemplates.templateKey],
                    templateId = row[MessageTemplates.templateId]
                )
            }
    }

    companion object {
        /** 轮播图条数上限：再多用户也划不完，且每条都是一张待下载的图片 */
        private const val MAX_BANNERS = 10

        /** 公告条数上限：首页公告栏是纵向滚动播报，条数过多会拉长单条停留间隔 */
        private const val MAX_NOTICES = 5
    }
}

@Serializable
data class BannerDto(
    val id: Long,
    val title: String,
    val imageUrl: String,
    /** none/page/webview，取值见 ContentLinkType */
    val linkType: String,
    val linkTarget: String?
)

@Serializable
data class NoticeDto(
    val id: Long,
    val content: String,
    val linkType: String,
    val linkTarget: String?
)

@Serializable
data class HomeContentDto(
    val banners: List<BannerDto>,
    val notices: List<NoticeDto>
)

@Serializable
data class SubscribeTemplateDto(
    /** 业务模板标识（appointment_remind_evening/appointment_remind_morning/points_expire_remind） */
    val templateKey: String,
    /** 微信公众平台模板 ID，用于 wx.requestSubscribeMessage */
    val templateId: String
)
