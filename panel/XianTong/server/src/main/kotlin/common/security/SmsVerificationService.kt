package com.dentalclinic.common.security

import com.dentalclinic.common.BaseService
import com.dentalclinic.common.sms.SmsConfig
import com.dentalclinic.common.sms.TencentSmsClient
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * 短信验证码服务（P1-10：手机号修改验证码校验）。
 *
 * 发送通道由 SmsConfig 决定：腾讯云短信凭证齐全时真实下发，缺失时降级为日志输出（仅限本地联调）。
 * 验证码仍存内存，多实例部署需替换为 Redis（多实例间不共享，换个实例校验会失败）。
 */
class SmsVerificationService : BaseService() {

    private val logger = LoggerFactory.getLogger("SmsVerificationService")
    private val random = SecureRandom()

    // 内存存储：phone -> (code, expiresAt)
    // 生产环境应替换为 Redis
    private val codeStore = mutableMapOf<String, CodeEntry>()

    /**
     * 发送验证码到指定手机号。
     *
     * @param phone 手机号
     * @return 发送结果
     */
    suspend fun sendVerificationCode(phone: String): SendResult {
        // 频率限制：同一手机号 60 秒内只能发一次
        val existing = codeStore[phone]
        if (existing != null && existing.sentAt.plusSeconds(60) > LocalDateTime.now()) {
            return SendResult.Error("发送过于频繁，请 60 秒后重试")
        }

        // 生成 6 位数字验证码
        val code = String.format("%06d", random.nextInt(1000000))
        val expiresAt = LocalDateTime.now().plusMinutes(5)

        // 先发送再落库：发送失败不写验证码，避免用户拿到「已发送」却永远收不到
        if (SmsConfig.tencent.enabled) {
            runCatching { TencentSmsClient.sendVerificationCode(phone, code) }
                .onFailure { e ->
                    logger.error("[SMS] 验证码发送失败 phone={}：{}", phone, e.message)
                    return SendResult.Error("验证码发送失败，请稍后重试")
                }
        } else {
            logger.info("[SMS] 未配置腾讯云短信，验证码仅输出日志（mock）phone={} code={}", phone, code)
        }

        codeStore[phone] = CodeEntry(code, expiresAt, LocalDateTime.now())

        return SendResult.Success
    }

    /**
     * 验证验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     * @return 验证结果
     */
    suspend fun verifyCode(phone: String, code: String): VerifyResult {
        val entry = codeStore[phone]
            ?: return VerifyResult.Error("验证码不存在或已过期")

        if (entry.expiresAt < LocalDateTime.now()) {
            codeStore.remove(phone)
            return VerifyResult.Error("验证码已过期")
        }

        if (entry.code != code) {
            return VerifyResult.Error("验证码错误")
        }

        // 验证成功，清除验证码（一次性使用）
        codeStore.remove(phone)
        return VerifyResult.Success
    }

    @Serializable
    sealed class SendResult {
        @Serializable
        object Success : SendResult()

        @Serializable
        data class Error(val message: String) : SendResult()
    }

    @Serializable
    sealed class VerifyResult {
        @Serializable
        object Success : VerifyResult()

        @Serializable
        data class Error(val message: String) : VerifyResult()
    }

    private data class CodeEntry(
        val code: String,
        val expiresAt: LocalDateTime,
        val sentAt: LocalDateTime
    )
}
