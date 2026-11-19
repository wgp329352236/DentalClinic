package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.Notifications
import com.dentalclinic.common.BaseService
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Notification service for user messages.
 */
class NotificationService : BaseService() {

    /**
     * Get user's notification list with pagination.
     */
    suspend fun getNotifications(userId: Long, msgType: String?, isRead: Boolean?, page: Int, pageSize: Int): NotificationListResponse = dbQuery {
        // Build where condition
        var whereOp: Op<Boolean> = Notifications.userId eq userId
        
        if (!msgType.isNullOrEmpty()) {
            whereOp = whereOp and (Notifications.msgType eq msgType)
        }
        
        if (isRead != null) {
            whereOp = whereOp and (Notifications.isRead eq (if (isRead) 1 else 0))
        }

        val total = Notifications.selectAll()
            .where { whereOp }
            .count()

        val rows = Notifications.selectAll()
            .where { whereOp }
            .orderBy(Notifications.createdAt, SortOrder.DESC)
            .limit(pageSize)
            .offset((page - 1).toLong())
            .toList()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val notifications = rows.map { row ->
            NotificationDto(
                id = row[Notifications.id],
                title = row[Notifications.title],
                content = row[Notifications.content],
                msgType = row[Notifications.msgType],
                isRead = row[Notifications.isRead] == 1,
                relatedId = row[Notifications.relatedId],
                createdAt = row[Notifications.createdAt].format(formatter),
                readAt = row[Notifications.readAt]?.format(formatter)
            )
        }

        NotificationListResponse(
            list = notifications,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    /**
     * Get unread message count.
     */
    suspend fun getUnreadCount(userId: Long): Int = dbQuery {
        Notifications.selectAll()
            .where {
                (Notifications.userId eq userId) and
                (Notifications.isRead eq 0)
            }
            .count()
            .toInt()
    }

    /**
     * Mark a single notification as read.
     */
    suspend fun markAsRead(userId: Long, notificationId: Long): Boolean = dbQuery {
        val updated = Notifications.update({
            (Notifications.id eq notificationId) and
            (Notifications.userId eq userId) and
            (Notifications.isRead eq 0)
        }) {
            it[Notifications.isRead] = 1
            it[Notifications.readAt] = LocalDateTime.now()
        }

        updated > 0
    }

    /**
     * Mark all notifications as read for a user.
     */
    suspend fun markAllAsRead(userId: Long): Int = dbQuery {
        Notifications.update({
            (Notifications.userId eq userId) and
            (Notifications.isRead eq 0)
        }) {
            it[Notifications.isRead] = 1
            it[Notifications.readAt] = LocalDateTime.now()
        }
    }

    /**
     * Create a new notification for a user.
     */
    suspend fun createNotification(userId: Long, title: String, content: String, msgType: String, relatedId: Long?): Long = dbQuery {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.title] = title
            it[Notifications.content] = content
            it[Notifications.msgType] = msgType
            it[Notifications.isRead] = 0
            if (relatedId != null) {
                it[Notifications.relatedId] = relatedId
            }
        } get Notifications.id
    }
}

// DTOs

@Serializable
data class NotificationDto(
    val id: Long,
    val title: String,
    val content: String,
    val msgType: String,
    val isRead: Boolean,
    val relatedId: Long?,
    val createdAt: String,
    val readAt: String?
)

@Serializable
data class NotificationListResponse(
    val list: List<NotificationDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)
