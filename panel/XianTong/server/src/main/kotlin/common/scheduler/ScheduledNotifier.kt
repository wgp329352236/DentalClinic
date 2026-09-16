package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.MessageTemplates
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.wx.WechatMiniClient
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.slf4j.LoggerFactory

/**
 * 定时任务专用通知通道。
 *
 * 策略（需求 3.6：预约提醒走微信订阅消息）：
 * 1. 按 templateKey 从 message_template 查启用中的微信模板 ID，并取用户 openid；
 * 2. 调 [WechatMiniClient.sendSubscribeMessage] 发订阅消息；
 * 3. 未配置模板 / 用户未授权（43101）/ 网络异常等任何失败，**降级写站内通知**，
 *    保证用户在小程序消息中心至少能看到提醒，不允许定时任务静默丢消息。
 *
 * 与在线接口里的事务不同：HTTP 调用微信不能持有数据库连接（超时最长 10s），
 * 因此先在事务内取 openid/模板 ID，HTTP 在事务外发，失败后再开新事务写站内信。
 */
object ScheduledNotifier : BaseService() {

    private val logger = LoggerFactory.getLogger("ScheduledNotifier")

    private data class Channel(val openid: String, val templateId: String?)

    suspend fun notify(
        userId: Long,
        templateKey: String,
        title: String,
        content: String,
        subscribeData: Map<String, String>,
        page: String? = null,
        msgType: String = "system",
        relatedId: Long? = null
    ) {
        val channel = dbQuery {
            val openid = Users.selectAll()
                .where { Users.id eq userId }
                .toList()
                .singleOrNull()
                ?.get(Users.openid)
                .orEmpty()
            val templateId = MessageTemplates.selectAll()
                .where { (MessageTemplates.templateKey eq templateKey) and (MessageTemplates.status eq 1) }
                .toList()
                .singleOrNull()
                ?.get(MessageTemplates.templateId)
                ?.takeIf { it.isNotBlank() }
            Channel(openid, templateId)
        }

        val sent = channel.templateId != null &&
            WechatMiniClient.sendSubscribeMessage(
                openid = channel.openid,
                templateId = channel.templateId,
                data = subscribeData,
                page = page
            )
        if (sent) return

        dbQuery {
            sendNotification(
                userId = userId,
                title = title,
                content = content,
                msgType = msgType,
                relatedId = relatedId
            )
        }
        logger.debug("[ScheduledNotifier]: 用户 {} 订阅消息不可达，已降级站内信（{}）", userId, templateKey)
    }
}
