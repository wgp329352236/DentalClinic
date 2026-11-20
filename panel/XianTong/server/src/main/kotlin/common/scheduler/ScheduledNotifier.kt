package com.dentalclinic.common.scheduler

import com.dentalclinic.admin.entity.MessageTemplates
import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.BaseService
import com.dentalclinic.common.wx.WechatMiniClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Channel(val openid: String, val templateId: String?)

    private data class Recipient(val userId: Long, val openid: String)

    suspend fun notify(
        userId: Long,
        templateKey: String,
        title: String,
        content: String,
        subscribeData: Map<String, String> = emptyMap(),
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

    /**
     * 在后台发送单用户通知，不阻塞主业务响应。
     *
     * 预约提交等即时业务只负责把核心数据提交到数据库，通知失败不能反向影响已成功的业务。
     */
    fun notifyAsync(
        userId: Long,
        templateKey: String,
        title: String,
        content: String,
        subscribeData: Map<String, String> = emptyMap(),
        page: String? = null,
        msgType: String = "system",
        relatedId: Long? = null
    ) {
        scope.launch {
            try {
                notify(
                    userId = userId,
                    templateKey = templateKey,
                    title = title,
                    content = content,
                    subscribeData = subscribeData,
                    page = page,
                    msgType = msgType,
                    relatedId = relatedId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("[ScheduledNotifier]: 用户 {} 异步通知失败（{}）", userId, templateKey, e)
            }
        }
    }

    /**
     * 向全部有效用户发送站内信，并尽力并行发送微信订阅消息。
     *
     * 活动发布属于运营群发：站内信必须先落库，即使用户未订阅或微信接口失败也能在消息中心看到；
     * 微信调用与写库分离，且并发受限，避免活动保存请求被逐用户 HTTP 调用拖住。
     */
    suspend fun notifyActiveUsers(
        templateKey: String,
        title: String,
        content: String,
        subscribeData: Map<String, String> = emptyMap(),
        page: String? = null,
        msgType: String = "activity",
        relatedId: Long? = null
    ) {
        val (recipients, templateId) = dbQuery {
            val users = Users.selectAll()
                .where { Users.status eq 1 }
                .toList()
                .map { Recipient(it[Users.id], it[Users.openid]) }
            val template = MessageTemplates.selectAll()
                .where { (MessageTemplates.templateKey eq templateKey) and (MessageTemplates.status eq 1) }
                .toList()
                .singleOrNull()
                ?.get(MessageTemplates.templateId)
                ?.takeIf { it.isNotBlank() }
            users to template
        }
        if (recipients.isEmpty()) return

        // 站内信是必达通道，不依赖用户是否授权过微信订阅消息。
        dbQuery {
            recipients.forEach { recipient ->
                sendNotification(
                    userId = recipient.userId,
                    title = title,
                    content = content,
                    msgType = msgType,
                    relatedId = relatedId
                )
            }
        }

        if (templateId.isNullOrBlank()) {
            logger.info("[ScheduledNotifier]: {} 未配置微信模板，已为 {} 位用户写入站内信", templateKey, recipients.size)
            return
        }

        val semaphore = Semaphore(5)
        var sent = 0
        recipients.chunked(100).forEach { chunk ->
            val results = coroutineScope {
                chunk.map { recipient ->
                    async {
                        semaphore.withPermit {
                            WechatMiniClient.sendSubscribeMessage(
                                openid = recipient.openid,
                                templateId = templateId,
                                data = subscribeData,
                                page = page
                            )
                        }
                    }
                }.awaitAll()
            }
            sent += results.count { it }
        }
        logger.info(
            "[ScheduledNotifier]: {} 群发完成，站内信 {} 条，微信订阅消息成功 {} 条",
            templateKey, recipients.size, sent
        )
    }

    /** 活动发布后的群发不阻塞管理端保存请求。 */
    fun notifyActiveUsersAsync(
        templateKey: String,
        title: String,
        content: String,
        subscribeData: Map<String, String> = emptyMap(),
        page: String? = null,
        msgType: String = "activity",
        relatedId: Long? = null
    ) {
        scope.launch {
            try {
                notifyActiveUsers(
                    templateKey = templateKey,
                    title = title,
                    content = content,
                    subscribeData = subscribeData,
                    page = page,
                    msgType = msgType,
                    relatedId = relatedId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("[ScheduledNotifier]: {} 群发失败", templateKey, e)
            }
        }
    }
}
