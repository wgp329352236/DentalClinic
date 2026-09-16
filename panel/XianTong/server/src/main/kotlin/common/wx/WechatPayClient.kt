package com.dentalclinic.common.wx

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 微信支付侧错误（下单/查单/验签失败）。 */
class WechatPayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** JSAPI 调起支付所需参数（小程序 wx.requestPayment 入参，appId 由小程序自身持有）。 */
data class JsapiPayParams(
    val timeStamp: String,
    val nonceStr: String,
    val packageValue: String,
    val signType: String,
    val paySign: String
)

/** 解密后的支付通知关键信息。 */
data class WxPayNotification(
    val outTradeNo: String,
    val transactionId: String,
    val tradeState: String,
    val totalFen: Long
)

/** 查单结果。 */
data class WxOrderState(
    val tradeState: String,
    val transactionId: String?
)

/**
 * 微信支付 V3 API 客户端（JSAPI / 小程序支付）。
 *
 * 不依赖官方/第三方 SDK，全部用 JDK 内置能力实现：
 * - 请求签名：SHA256-RSA2048，签名串 `方法\n规范URL\n时间戳\n随机串\n报文主体\n`
 * - 支付回调：用微信支付平台公钥（证书）验证 Wechatpay-Signature，
 *   并用 APIv3 密钥对 resource 做 AEAD_AES_256_GCM 解密
 * - 平台证书：启动后按需调 /v3/certificates 拉取并按序列号缓存（APIv3 密钥解密）
 *
 * 安全红线：回调只信「验签通过 + 解密成功 + 金额与本系统订单一致」三者同时成立，
 * 任何一步失败都不得入账（由 RechargeService 决定响应 FAIL 让微信重试）。
 */
object WechatPayClient {

    private val logger = LoggerFactory.getLogger("WechatPayClient")

    private const val BASE = "https://api.mch.weixin.qq.com"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val certMutex = Mutex()

    @Volatile private var platformCerts: Map<String, X509Certificate> = emptyMap()

    // ==================== JSAPI 下单 / 调起 ====================

    /**
     * 统一下单（JSAPI），返回 prepay_id。
     *
     * @param amountFen 支付金额，单位「分」
     * @param description 商品描述（UTF-8，<=127 字节）
     */
    suspend fun createJsapiOrder(
        outTradeNo: String,
        amountFen: Long,
        description: String,
        openid: String
    ): String {
        val cfg = WechatConfig.pay
        if (!cfg.enabled) throw WechatPayException("微信支付未完成配置，无法统一下单")

        val req = JsapiOrderRequest(
            appid = WechatConfig.miniApp.appId,
            mchid = cfg.mchId,
            description = description,
            outTradeNo = outTradeNo,
            notifyUrl = cfg.notifyUrl,
            amount = JsapiAmount(total = amountFen),
            payer = JsapiPayer(openid = openid)
        )
        val body = json.encodeToString(JsapiOrderRequest.serializer(), req)
        val path = "/v3/pay/transactions/jsapi"
        val resp = signedRequest(HttpMethod.Post, path, body)
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            throw WechatPayException("微信统一下单失败 ${resp.status.value}: $text")
        }
        val prepayId = json.decodeFromString(JsapiOrderResponse.serializer(), text).prepayId
        if (prepayId.isNullOrBlank()) throw WechatPayException("微信统一下单未返回 prepay_id: $text")
        return prepayId
    }

    /**
     * 生成小程序 wx.requestPayment 的调起参数，并对调起串做二次签名。
     * 签名串：`appId\n时间戳\n随机串\nprepay_id=xxx\n`
     */
    fun buildJsapiPayParams(prepayId: String): JsapiPayParams {
        val timeStamp = System.currentTimeMillis() / 1000L
        val nonce = randomNonce()
        val pkg = "prepay_id=$prepayId"
        val message = "${WechatConfig.miniApp.appId}\n$timeStamp\n$nonce\n$pkg\n"
        return JsapiPayParams(
            timeStamp = timeStamp.toString(),
            nonceStr = nonce,
            packageValue = pkg,
            signType = "RSA",
            paySign = signWithMerchantKey(message)
        )
    }

    // ==================== 查单 ====================

    /**
     * 主动查单（回调丢失时的兜底对账路径）。订单不存在返回 null（NOTPAY 早期可能查不到）。
     */
    suspend fun queryOrderByOutTradeNo(outTradeNo: String): WxOrderState? {
        val cfg = WechatConfig.pay
        // 规范 URL 必须包含查询串，否则签名不通过
        val path = "/v3/pay/transactions/out-trade-no/$outTradeNo?mchid=${cfg.mchId}"
        val resp = signedRequest(HttpMethod.Get, path, "")
        val text = resp.bodyAsText()
        if (resp.status.value == 404) return null
        if (!resp.status.isSuccess()) {
            throw WechatPayException("微信查单失败 ${resp.status.value}: $text")
        }
        val dto = json.decodeFromString(QueryOrderResponse.serializer(), text)
        val state = dto.tradeState ?: return null
        return WxOrderState(tradeState = state, transactionId = dto.transactionId)
    }

    // ==================== 支付回调 ====================

    /**
     * 校验回调签名并解密报文。
     *
     * @param rawBody 微信推送的原始请求体文本（必须逐字节参与验签，不能先反序列化再拼）
     * @param headers 回调请求头
     * @throws WechatPayException 缺少头/证书未知/验签失败/解密失败
     */
    suspend fun verifyAndDecryptNotification(rawBody: String, headers: Headers): WxPayNotification {
        val timestamp = headers["Wechatpay-Timestamp"]
            ?: throw WechatPayException("回调缺少 Wechatpay-Timestamp 头")
        val nonce = headers["Wechatpay-Nonce"]
            ?: throw WechatPayException("回调缺少 Wechatpay-Nonce 头")
        val serial = headers["Wechatpay-Serial"]
            ?: throw WechatPayException("回调缺少 Wechatpay-Serial 头")
        val signatureB64 = headers["Wechatpay-Signature"]
            ?: throw WechatPayException("回调缺少 Wechatpay-Signature 头")

        val cert = resolvePlatformCert(serial)
        val message = "$timestamp\n$nonce\n$rawBody\n"
        if (!verifyWithPlatformCert(cert, message, signatureB64)) {
            throw WechatPayException("支付回调签名验证失败")
        }

        val envelope = json.decodeFromString(NotifyEnvelope.serializer(), rawBody)
        val resource = envelope.resource
            ?: throw WechatPayException("回调报文缺少 resource")
        val plaintext = decryptAesGcm(
            apiV3Key = WechatConfig.pay.apiV3Key,
            nonce = resource.nonce,
            associatedData = resource.associatedData ?: "",
            ciphertextB64 = resource.ciphertext
        )
        val txn = json.decodeFromString(NotifyTransaction.serializer(), plaintext)
        return WxPayNotification(
            outTradeNo = txn.outTradeNo
                ?: throw WechatPayException("解密后的支付结果缺少 out_trade_no"),
            transactionId = txn.transactionId ?: "",
            tradeState = txn.tradeState ?: "",
            totalFen = txn.amount?.total ?: 0L
        )
    }

    // ==================== 平台证书 ====================

    /** 按序列号取平台证书；本地没有则拉取一次，再没有则失败（拒绝未知证书签名）。 */
    private suspend fun resolvePlatformCert(serial: String): X509Certificate {
        platformCerts[serial]?.let { return it }
        refreshPlatformCerts()
        return platformCerts[serial]
            ?: throw WechatPayException("未知的微信支付平台证书序列号: $serial")
    }

    /**
     * 拉取并解密平台证书。证书列表响应本身也带签名，解密后用响应头指定序列号的新证书
     * 回验响应签名，构成自举信任链（首次拉取没有别的锚点可用，这是微信官方推荐做法）。
     */
    private suspend fun refreshPlatformCerts() = certMutex.withLock {
        // double-check：等锁期间可能已被其他协程刷新
        val path = "/v3/certificates?mchid=${WechatConfig.pay.mchId}"
        val resp = signedRequest(HttpMethod.Get, path, "")
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            throw WechatPayException("拉取平台证书失败 ${resp.status.value}: $text")
        }

        val dto = json.decodeFromString(CertificateListResponse.serializer(), text)
        val decoded = linkedMapOf<String, X509Certificate>()
        dto.data?.forEach { item ->
            val enc = item.encryptCertificate ?: return@forEach
            val pem = decryptAesGcm(
                apiV3Key = WechatConfig.pay.apiV3Key,
                nonce = enc.nonce,
                associatedData = enc.associatedData ?: "",
                ciphertextB64 = enc.ciphertext
            )
            decoded[item.serialNo] = parseCertificate(pem)
        }
        if (decoded.isEmpty()) throw WechatPayException("平台证书列表为空")

        // 回验证书列表响应签名
        val signSerial = resp.headers["Wechatpay-Serial"]
        val signCert = signSerial?.let { decoded[it] }
            ?: throw WechatPayException("证书列表响应缺少可信签名证书")
        val message = "${resp.headers["Wechatpay-Timestamp"]}\n" +
            "${resp.headers["Wechatpay-Nonce"]}\n$text\n"
        val signature = resp.headers["Wechatpay-Signature"]
            ?: throw WechatPayException("证书列表响应缺少签名")
        if (!verifyWithPlatformCert(signCert, message, signature)) {
            throw WechatPayException("平台证书列表响应签名验证失败")
        }

        platformCerts = decoded
        logger.info("[WechatPay]: 已刷新平台证书，共 ${decoded.size} 张")
    }

    // ==================== 签名 / 加解密原语 ====================

    /** 对 V3 请求签名并附带 Authorization 头发出。GET 的 body 必须为空串参与签名。 */
    private suspend fun signedRequest(
        method: HttpMethod,
        canonicalUrlWithQuery: String,
        body: String
    ): HttpResponse {
        val timestamp = System.currentTimeMillis() / 1000L
        val nonce = randomNonce()
        val signBase = "${method.value}\n$canonicalUrlWithQuery\n$timestamp\n$nonce\n$body\n"
        val signature = signWithMerchantKey(signBase)
        val authorization = "WECHATPAY2-SHA256-RSA2048 " +
            "mchid=\"${WechatConfig.pay.mchId}\"," +
            "nonce_str=\"$nonce\"," +
            "timestamp=\"$timestamp\"," +
            "serial_no=\"${WechatConfig.pay.merchantSerial}\"," +
            "signature=\"$signature\""

        return client.request("$BASE$canonicalUrlWithQuery") {
            this.method = method
            headers.append("Authorization", authorization)
            headers.append("Accept", "application/json")
            headers.append("User-Agent", "DentalClinic-Ktor/1.0")
            if (body.isNotEmpty()) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private fun signWithMerchantKey(message: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(loadMerchantPrivateKey())
        signature.update(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun verifyWithPlatformCert(
        cert: X509Certificate,
        message: String,
        signatureB64: String
    ): Boolean = try {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(cert.publicKey)
        signature.update(message.toByteArray(Charsets.UTF_8))
        signature.verify(Base64.getDecoder().decode(signatureB64))
    } catch (e: Exception) {
        logger.warn("[WechatPay]: 验签异常: ${e.message}")
        false
    }

    /** 解析 PKCS#8 PEM 商户私钥（apiclient_key.pem）。 */
    private fun loadMerchantPrivateKey(): PrivateKey {
        val pem = WechatConfig.pay.privateKeyPem
        val base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun parseCertificate(pem: String): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)))
            as X509Certificate
    }

    /**
     * AEAD_AES_256_GCM 解密（APIv3 回调解密与平台证书解密）。
     * JDK 的 GCM 实现期望密文末尾自带 16 字节认证标签，微信密文格式正好如此。
     */
    private fun decryptAesGcm(
        apiV3Key: String,
        nonce: String,
        associatedData: String,
        ciphertextB64: String
    ): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(apiV3Key.toByteArray(Charsets.UTF_8), "AES"),
                GCMParameterSpec(128, nonce.toByteArray(Charsets.UTF_8))
            )
            if (associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
            }
            val plain = cipher.doFinal(Base64.getDecoder().decode(ciphertextB64))
            return String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            throw WechatPayException("AEAD_GCM 解密失败（APIv3 密钥或报文有误）: ${e.message}", e)
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ==================== 报文 DTO ====================

    @Serializable
    private data class JsapiOrderRequest(
        val appid: String,
        val mchid: String,
        val description: String,
        @kotlinx.serialization.SerialName("out_trade_no") val outTradeNo: String,
        @kotlinx.serialization.SerialName("notify_url") val notifyUrl: String,
        val amount: JsapiAmount,
        val payer: JsapiPayer
    )

    @Serializable
    private data class JsapiAmount(
        val total: Long,
        val currency: String = "CNY"
    )

    @Serializable
    private data class JsapiPayer(
        val openid: String
    )

    @Serializable
    private data class JsapiOrderResponse(
        @kotlinx.serialization.SerialName("prepay_id") val prepayId: String? = null
    )

    @Serializable
    private data class QueryOrderResponse(
        @kotlinx.serialization.SerialName("transaction_id") val transactionId: String? = null,
        @kotlinx.serialization.SerialName("trade_state") val tradeState: String? = null
    )

    @Serializable
    private data class NotifyEnvelope(
        val id: String? = null,
        @kotlinx.serialization.SerialName("event_type") val eventType: String? = null,
        val resource: NotifyResource? = null
    )

    @Serializable
    private data class NotifyResource(
        val algorithm: String? = null,
        val nonce: String = "",
        @kotlinx.serialization.SerialName("associated_data") val associatedData: String? = null,
        val ciphertext: String = ""
    )

    @Serializable
    private data class NotifyTransaction(
        @kotlinx.serialization.SerialName("out_trade_no") val outTradeNo: String? = null,
        @kotlinx.serialization.SerialName("transaction_id") val transactionId: String? = null,
        @kotlinx.serialization.SerialName("trade_state") val tradeState: String? = null,
        val amount: NotifyAmount? = null
    )

    @Serializable
    private data class NotifyAmount(
        val total: Long? = null
    )

    @Serializable
    private data class CertificateListResponse(
        val data: List<CertificateItem>? = null
    )

    @Serializable
    private data class CertificateItem(
        @kotlinx.serialization.SerialName("serial_no") val serialNo: String,
        @kotlinx.serialization.SerialName("encrypt_certificate") val encryptCertificate: EncryptCertificate? = null
    )

    @Serializable
    private data class EncryptCertificate(
        val algorithm: String? = null,
        val nonce: String = "",
        @kotlinx.serialization.SerialName("associated_data") val associatedData: String? = null,
        val ciphertext: String = ""
    )
}
