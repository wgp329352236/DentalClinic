package com.dentalclinic.admin.dto.request

import kotlinx.serialization.Serializable

// ==================== 充值档位 ====================

/**
 * 充值档位新增/编辑请求。
 * @param bonusRate 赠送比例，0.1000 表示充 1000 送 10%；取值范围 0~1
 */
@Serializable
data class RechargeTierUpsertRequest(
    val tierName: String,
    val minAmount: Double,
    val bonusRate: Double,
    val status: Int = 1,
    val sortOrder: Int = 0
)

// ==================== 会员等级 ====================

/**
 * 会员等级新增/编辑请求。
 * @param levelCode 等级编码（唯一，如 1~4）
 * @param discountRate 折扣比例，0.95 表示 95 折，1.00 表示不打折
 * @param benefits 权益描述，纯文本/JSON 字符串均可
 */
@Serializable
data class MemberLevelUpsertRequest(
    val levelCode: Int,
    val levelName: String,
    val minConsumption: Double,
    val discountRate: Double,
    val benefits: String? = null,
    val status: Int = 1
)

// ==================== 消息模板 ====================

/**
 * 微信订阅消息模板新增/编辑请求。
 *
 * 内置模板（appointment_remind_evening/appointment_remind_morning/points_expire_remind）
 * 的 templateKey 不允许修改，只可编辑标题/内容/微信 templateId/状态。
 */
@Serializable
data class MessageTemplateUpsertRequest(
    val templateKey: String,
    val templateId: String = "",
    val title: String,
    val content: String,
    val triggerEvent: String? = null,
    val status: Int = 1
)

// ==================== 客户标签 ====================

@Serializable
data class CustomerTagUpsertRequest(
    val tagName: String,
    /** ant 标签色：blue/green/orange/red/purple 等 */
    val color: String = "blue",
    val remark: String? = null,
    val status: Int = 1
)

/** 给客户打标签：全量覆盖（空列表=清空该客户全部标签）。 */
@Serializable
data class SetUserTagsRequest(
    val tagIds: List<Long> = emptyList()
)
