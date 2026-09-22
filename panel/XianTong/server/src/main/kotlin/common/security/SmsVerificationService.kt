package com.dentalclinic.common.security

import com.dentalclinic.common.BaseService
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * 短信验证码服务（P1-10：手机号修改验证码校验框架）。
 *
 * 当前实现为内存存储 + 日志输出，生产环境需替换为：
 * 1. Redis 存储验证码（支持分布式）
 * 2. 真实 SMS 提供商（阿里云短信、腾讯云短信等）
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

        // 存储验证码
        codeStore[phone] = CodeEntry(code, expiresAt, LocalDateTime.now())

        // 发送短信（当前为 mock，仅日志输出）
        // TODO: 接入真实 SMS 提供商
        logger.info("[SMS] 验证码已发送到 $phone: $code (有效期 5 分钟)")

        // 模拟 SMS 发送延迟
        delay(100)

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
