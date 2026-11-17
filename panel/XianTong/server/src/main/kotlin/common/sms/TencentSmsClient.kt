package com.dentalclinic.common.sms

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 短信发送失败（未配置、网络异常、腾讯云返回错误码）。 */
class SmsSendException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 腾讯云短信客户端（SendSms，API 版本 2021-01-11）。
 *
 * 与 WechatPayClient 一致，不引入服务商 SDK，用 JDK 内置 HMAC-SHA256 + Ktor CIO 直接调 OpenAPI：
 * 签名算法 TC3-HMAC-SHA256，签名串 = 算法\n时间戳\n日期/服务/tc3_request\n规范化请求哈希。
 *
 * 注意：调用方必须处理 [SmsSendException]，不得把验证码下发失败当成成功。
 */
object TencentSmsClient {

    private val logger = LoggerFactory.getLogger("TencentSmsClient")

    private const val HOST = "sms.tencentcloudapi.com"
    private const val SERVICE = "sms"
    private const val ACTION = "SendSms"
    private const val VERSION = "2021-01-11"
    private const val ALGORITHM = "TC3-HMAC-SHA256"
    private const val CONTENT_TYPE = "application/json; charset=utf-8"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 发送验证码短信。
     *
     * @param phone 用户手机号，支持 11 位国内号码或 +86 前缀
     * @param code 验证码，作为模板第一个参数 {1} 传入
     * @throws SmsSendException 未配置、手机号非法、网络失败、腾讯云返回非 Ok
     */
    suspend fun sendVerificationCode(phone: String, code: String) {
        val cfg = SmsConfig.tencent
        if (!cfg.enabled) throw SmsSendException("腾讯云短信未完成配置，无法发送验证码")

        val payload = json.encodeToString(
            SendSmsRequest.serializer(),
            SendSmsRequest(
                phoneNumberSet = listOf(normalizePhone(phone)),
                smsSdkAppId = cfg.sdkAppId,
                signName = cfg.signName,
                templateId = cfg.templateId,
                templateParamSet = listOf(code)
            )
        )
        val timestamp = System.currentTimeMillis() / 1000L

        val response = client.post("https://$HOST") {
            // 与签名串里的 content-type 值逐字符一致，避免服务端重算规范化请求时签名不匹配
            headers.append("Content-Type", CONTENT_TYPE)
            headers.append("Host", HOST)
            headers.append("X-TC-Action", ACTION)
            headers.append("X-TC-Version", VERSION)
            headers.append("X-TC-Timestamp", timestamp.toString())
            headers.append("X-TC-Region", cfg.region)
            headers.append("Authorization", buildAuthorization(cfg, payload, timestamp))
            setBody(payload)
        }

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw SmsSendException("腾讯云短信接口 HTTP ${response.status.value}: $text")
        }

        val body = json.decodeFromString(SendSmsResponse.serializer(), text).response
        body?.error?.let { throw SmsSendException("腾讯云短信返回错误 ${it.code}: ${it.message}") }
        val status = body?.sendStatusSet?.firstOrNull()
            ?: throw SmsSendException("腾讯云短信返回结果为空: $text")
        if (status.code != "Ok") {
            throw SmsSendException("腾讯云短信发送失败 ${status.code}: ${status.message}")
        }
        logger.info("[SMS]: 验证码短信已提交腾讯云，手机号 {}，序列号 {}", status.phoneNumber, status.serialNo)
    }

    // ==================== TC3-HMAC-SHA256 签名 ====================

    private fun buildAuthorization(cfg: SmsConfig.Tencent, payload: String, timestamp: Long): String {
        val date = utcDate(timestamp)
        val canonicalRequest = listOf(
            "POST", "/", "",
            "content-type:$CONTENT_TYPE", "host:$HOST", "",
            "content-type;host", sha256Hex(payload)
        ).joinToString("\n")
        val scope = "$date/$SERVICE/tc3_request"
        val stringToSign = listOf(
            ALGORITHM, timestamp.toString(), scope, sha256Hex(canonicalRequest)
        ).joinToString("\n")

        val secretSigning = hmacSha256(
            hmacSha256(hmacSha256(("TC3" + cfg.secretKey).toByteArray(), date), SERVICE),
            "tc3_request"
        )
        val signature = hmacSha256(secretSigning, stringToSign).toHex()
        return "$ALGORITHM Credential=${cfg.secretId}/$scope, SignedHeaders=content-type;host, Signature=$signature"
    }

    private fun utcDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestamp * 1000))

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()

    private fun hmacSha256(key: ByteArray, message: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(message.toByteArray())
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** 国内手机号统一补 +86 前缀；已带国家码的原样返回。 */
    private fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        val digits = trimmed.removePrefix("+86")
        if (!digits.matches(Regex("1\\d{10}"))) {
            throw SmsSendException("手机号格式不正确: $phone")
        }
        return if (trimmed.startsWith("+")) trimmed else "+86$digits"
    }

    // ==================== 报文 DTO ====================

    @Serializable
    private data class SendSmsRequest(
        @SerialName("PhoneNumberSet") val phoneNumberSet: List<String>,
        @SerialName("SmsSdkAppId") val smsSdkAppId: String,
        @SerialName("SignName") val signName: String,
        @SerialName("TemplateId") val templateId: String,
        @SerialName("TemplateParamSet") val templateParamSet: List<String>
    )

    @Serializable
    private data class SendSmsResponse(
        @SerialName("Response") val response: SendSmsResponseBody? = null
    )

    @Serializable
    private data class SendSmsResponseBody(
        @SerialName("SendStatusSet") val sendStatusSet: List<SendStatus>? = null,
        @SerialName("Error") val error: ApiError? = null,
        @SerialName("RequestId") val requestId: String? = null
    )

    @Serializable
    private data class SendStatus(
        @SerialName("Code") val code: String = "",
        @SerialName("Message") val message: String = "",
        @SerialName("PhoneNumber") val phoneNumber: String = "",
        @SerialName("SerialNo") val serialNo: String = ""
    )

    @Serializable
    private data class ApiError(
        @SerialName("Code") val code: String = "",
        @SerialName("Message") val message: String = ""
    )
}
