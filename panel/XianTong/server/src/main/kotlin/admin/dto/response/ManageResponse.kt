package com.dentalclinic.admin.dto.response

import kotlinx.serialization.Serializable

// ==================== 充值档位 ====================

@Serializable
data class RechargeTierAdminDto(
    val id: Long,
    val tierName: String,
    val minAmount: Double,
    val bonusRate: Double,
    /** 按门槛金额展示用赠送额（minAmount × bonusRate） */
    val bonusAmount: Double,
    val status: Int,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String
)

// ==================== 会员等级 ====================

@Serializable
data class MemberLevelDto(
    val id: Long,
    val levelCode: Int,
    val levelName: String,
    val minConsumption: Double,
    val discountRate: Double,
    val benefits: String?,
    val status: Int,
    /** 该等级当前关联客户数（删除前给前端明确提示） */
    val userCount: Int = 0,
    val createdAt: String
)

// ==================== 充值流水 ====================

@Serializable
data class RechargeRecordAdminDto(
    val id: Long,
    val userId: Long,
    val userName: String?,
    val userPhone: String?,
    val clinicId: Long,
    val clinicName: String,
    val amount: Double,
    val bonusAmount: Double,
    val paymentMethod: String,
    val wechatOrderId: String?,
    val outTradeNo: String?,
    /** pending/success/failed/refunded */
    val status: String,
    val paidAt: String?,
    val createdAt: String
)

@Serializable
data class RechargeRecordListResponse(
    val list: List<RechargeRecordAdminDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

// ==================== 客户标签 ====================

@Serializable
data class CustomerTagDto(
    val id: Long,
    val tagName: String,
    val color: String,
    val remark: String?,
    val status: Int
)

// ==================== 消息模板 ====================

@Serializable
data class MessageTemplateDto(
    val id: Long,
    val templateKey: String,
    val templateId: String,
    val title: String,
    val content: String,
    val triggerEvent: String,
    val status: Int,
    /** 内置模板只可编辑不可删除 */
    val builtIn: Boolean,
    val updatedAt: String
)
