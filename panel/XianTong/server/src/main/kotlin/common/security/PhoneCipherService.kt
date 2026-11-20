package com.dentalclinic.common.security

import com.dentalclinic.common.config.GlobalConfig
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 手机号加密存储服务。
 *
 * 数据库只保存：
 * - phone_cipher：AES-256-GCM 密文，格式 v1:base64(iv):base64(ciphertext)；
 * - phone_hash：以独立密钥计算的 HMAC-SHA256 盲索引，用于精确查询/唯一性约束。
 *
 * 使用 HMAC 而非裸 SHA-256，避免手机号低熵空间被字典表反推。phone 明文列仅保留给
 * 历史迁移兼容，迁移和所有新写入完成后写入 NULL，读取时只从密文解密。
 */
object PhoneCipherService {

    private val logger = LoggerFactory.getLogger("PhoneCipherService")
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    private const val VERSION = "v1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ENCRYPTION_DEFAULT = "phone-encryption-dev-key-change-me"
    private const val BLIND_INDEX_DEFAULT = "phone-blind-index-dev-key-change-me"

    private val aesKey: SecretKeySpec by lazy {
        val secret = GlobalConfig.requireSecret(
            envName = "PHONE_ENCRYPTION_KEY",
            yamlPath = "security.phoneEncryptionKey",
            devDefault = ENCRYPTION_DEFAULT,
            label = "手机号加密密钥"
        )
        // 配置允许任意长度的强随机字符串；固定派生为 32 字节 AES-256 密钥。
        SecretKeySpec(sha256(secret), "AES")
    }

    private val blindIndexKey: ByteArray by lazy {
        val secret = GlobalConfig.requireSecret(
            envName = "PHONE_BLIND_INDEX_KEY",
            yamlPath = "security.phoneBlindIndexKey",
            devDefault = BLIND_INDEX_DEFAULT,
            label = "手机号盲索引密钥"
        )
        sha256(secret)
    }

    data class EncryptedPhone(
        val ciphertext: String,
        val blindIndex: String
    )

    /**
     * 手机号归一化：去除空格、短横线、括号及 +86/86 国家码前缀。
     * 保留非数字字符的宽松处理能力，确保历史脏数据也能安全迁移。
     */
    fun normalize(phone: String): String {
        var digits = phone.trim().filter { it.isDigit() }
        if (digits.length == 13 && digits.startsWith("86")) digits = digits.removePrefix("86")
        return digits
    }

    /** 中国大陆手机号格式校验，供资料更新等写入入口使用。 */
    fun isValidMainlandMobile(phone: String): Boolean =
        normalize(phone).matches(Regex("^1[3-9]\\d{9}$"))

    /** 生成盲索引；同一归一化手机号在固定密钥下结果稳定。 */
    fun blindIndex(phone: String): String {
        val normalized = normalize(phone)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(blindIndexKey, "HmacSHA256"))
        return mac.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** AES-256-GCM 加密手机号并同时计算盲索引。 */
    fun encrypt(phone: String): EncryptedPhone {
        val normalized = normalize(phone)
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
        val payload = listOf(
            VERSION,
            encoder.encodeToString(iv),
            encoder.encodeToString(encrypted)
        ).joinToString(":")
        return EncryptedPhone(
            ciphertext = payload,
            blindIndex = blindIndex(normalized)
        )
    }

    /**
     * 解密手机号。密文损坏时返回 null 并记录告警，绝不静默回退到明文列。
     */
    fun decrypt(ciphertext: String): String? {
        return try {
            val parts = ciphertext.split(':', limit = 3)
            if (parts.size != 3 || parts[0] != VERSION) {
                logger.warn("[PhoneCipher] 不支持的手机号密文格式")
                return null
            }
            val iv = decoder.decode(parts[1])
            val encrypted = decoder.decode(parts[2])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // 不输出密文/异常中的敏感字节，只记录类型与 message
            logger.warn("[PhoneCipher] 手机号解密失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * 读取手机号：优先解密 phone_cipher；仅当密文列为空时兼容历史明文列。
     */
    fun readPhone(ciphertext: String?, plaintext: String?): String? {
        if (!ciphertext.isNullOrBlank()) return decrypt(ciphertext)
        return plaintext?.takeIf { it.isNotBlank() }
    }

    /** 已注销账号使用不可逆占位盲索引，密文清空，避免保留可恢复 PII。 */
    fun deletedBlindIndex(userId: Long): String = blindIndex("deleted-user-$userId")

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
}
