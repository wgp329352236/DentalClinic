package com.dentalclinic.common.wx

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/** 微信接口业务错误（errcode != 0 或网络层失败）。 */
class WechatApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** code2session 结果。 */
data class WxSession(
    val openid: String,
    val unionid: String?
)

/**
 * 微信小程序服务端接口客户端。
 *
 * - [jscode2session]：小程序登录凭证换 openid/session_key（auth.code2Session）
 * - [getPhoneNumber]：手机号快速授权动态令牌换手机号（wxa/business/getuserphonenumber）
 *
 * access_token 为进程内缓存（提前 5 分钟过期）。单实例部署足够；多实例部署需改存
 * Redis 等共享存储并加分布式锁，否则各实例分别刷新会使微信侧旧 token 提前失效。
 */
object WechatMiniClient {

    private val logger = LoggerFactory.getLogger("WechatMiniClient")

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

    private const val API_BASE = "https://api.weixin.qq.com"

    private val tokenMutex = Mutex()
    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var tokenExpireAtMillis: Long = 0L

    /**
     * 用 wx.login 拿到的 js_code 换取 openid。
     *
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     * code 只能用一次且 5 分钟过期，失败时把微信 errcode/errmsg 原样写入异常信息便于排查。
     */
    suspend fun jscode2session(code: String): WxSession {
        val cfg = WechatConfig.miniApp
        if (!cfg.enabled) throw WechatApiException("微信小程序未配置 appId/appSecret，无法调用 code2session")

        val url = "$API_BASE/sns/jscode2session" +
            "?appid=${cfg.appId}&secret=${cfg.appSecret}&js_code=$code&grant_type=authorization_code"

        val text = try {
            client.get(url).bodyAsText()
        } catch (e: Exception) {
            throw WechatApiException("调用微信 code2session 网络失败: ${e.message}", e)
        }

        val resp = json.decodeFromString<Code2SessionResp>(text)
        // 成功时无 errcode；失败时 errcode 非 0 且 openid 为空
        if (resp.errcode != null && resp.errcode != 0) {
            throw WechatApiException("微信 code2session 失败 errcode=${resp.errcode} errmsg=${resp.errmsg}")
        }
        val openid = resp.openid
            ?: throw WechatApiException("微信 code2session 返回缺少 openid: $text")
        return WxSession(openid = openid, unionid = resp.unionid)
    }

    /**
     * 用 getPhoneNumber 回调里的动态 code 换手机号。
     *
     * 该接口使用的是「接口调用凭据」access_token（cgi-bin/token），与用户登录态无关。
     * 手机号属于登录增强信息：调用失败不应阻断主登录流程，由调用方决定是否吞掉异常。
     */
    suspend fun getPhoneNumber(phoneCode: String): String? {
        val accessToken = getAccessToken()
        val text = try {
            client.post("$API_BASE/wxa/business/getuserphonenumber?access_token=$accessToken") {
                contentType(ContentType.Application.Json)
                setBody("""{"code":"$phoneCode"}""")
            }.bodyAsText()
        } catch (e: Exception) {
            throw WechatApiException("调用微信 getuserphonenumber 网络失败: ${e.message}", e)
        }

        val resp = json.decodeFromString<PhoneNumberResp>(text)
        if (resp.errcode != null && resp.errcode != 0) {
            throw WechatApiException("微信获取手机号失败 errcode=${resp.errcode} errmsg=${resp.errmsg}")
        }
        return resp.phoneInfo?.phoneNumber?.takeIf { it.isNotBlank() }
    }

    /**
     * 发送小程序「订阅消息」（一次性订阅，用户每授权一次可下发一条）。
     *
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/mp-message-management/subscribe-message-send.html
     *
     * 与 [jscode2session] 不同，本方法**永不抛异常**：定时任务批量提醒时，单用户失败
     * （43101 用户未订阅/未授权、40003 openid 无效、47003 模板参数不符、网络超时等）
     * 只记 WARN 并返回 false，由调用方降级为站内信，绝不允许中断整批任务。
     *
     * @param data 模板字段，key 为微信模板里的字段名（如 thing1/time2），value 为展示文本
     * @return true 微信受理成功（errcode=0）；false 未配置/未订阅/发送失败，调用方应降级站内信
     */
    suspend fun sendSubscribeMessage(
        openid: String,
        templateId: String,
        data: Map<String, String>,
        page: String? = null
    ): Boolean {
        val cfg = WechatConfig.miniApp
        if (!cfg.enabled || openid.isBlank() || templateId.isBlank()) return false

        val body = buildJsonObject {
            put("touser", openid)
            put("template_id", templateId)
            if (!page.isNullOrBlank()) put("page", page)
            put("miniprogram_state", "formal")
            put("lang", "zh_CN")
            putJsonObject("data") {
                data.forEach { (key, value) ->
                    putJsonObject(key) { put("value", value) }
                }
            }
        }

        val text = try {
            val token = getAccessToken()
            client.post("$API_BASE/cgi-bin/message/subscribe/send?access_token=$token") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.bodyAsText()
        } catch (e: Exception) {
            logger.warn("[Wechat]: 订阅消息网络异常 openid={} tpl={}: {}", openid, templateId, e.message)
            return false
        }

        val resp = try {
            json.decodeFromString<SubscribeSendResp>(text)
        } catch (e: Exception) {
            logger.warn("[Wechat]: 订阅消息响应解析失败: {}", text)
            return false
        }
        if (resp.errcode == 0) return true
        logger.warn(
            "[Wechat]: 订阅消息发送失败 openid={} tpl={} errcode={} errmsg={}",
            openid, templateId, resp.errcode, resp.errmsg
        )
        return false
    }

    /**
     * 生成小程序码（wxa/getwxacodeunlimit，扫码直接打开小程序指定页）。
     *
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/qrcode-link/getwxacodeunlimit.html
     *
     * @param scene 场景参数（≤32 个可见字符，不可含中文；扫码进入后在小程序 onLaunch/onShow
     *              的 options.scene 收到，形如 "promo=9"）
     * @param page 落地页路径（须为 app.json 已注册页面，不带 query）
     * @return 小程序码 PNG 图片字节；未配置微信凭证或微信侧失败返回 null，
     *         由调用方降级为普通二维码（不抛异常，保证功能可用）
     */
    suspend fun getWxaCodeUnlimit(scene: String, page: String): ByteArray? {
        val cfg = WechatConfig.miniApp
        if (!cfg.enabled) {
            logger.info("[Wechat]: 未配置小程序凭证，小程序码生成降级为普通二维码")
            return null
        }
        if (scene.isBlank() || scene.length > 32 || page.isBlank()) {
            logger.warn("[Wechat]: 小程序码参数不合法 scene='{}' page='{}'", scene, page)
            return null
        }

        val body = buildJsonObject {
            put("scene", scene)
            put("page", page)
            put("check_path", false)
            put("env_version", "release")
        }

        val response = try {
            val token = getAccessToken()
            client.post("$API_BASE/wxa/getwxacodeunlimit?access_token=$token") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Exception) {
            logger.warn("[Wechat]: 小程序码网络异常 scene={}: {}", scene, e.message)
            return null
        }

        val bytes = try {
            response.readRawBytes()
        } catch (e: Exception) {
            logger.warn("[Wechat]: 小程序码响应读取失败 scene={}: {}", scene, e.message)
            return null
        }
        if (bytes.isEmpty()) return null

        // 失败时微信返回 JSON（{errcode,errmsg}），成功时返回图片二进制：按首字节区分
        if (bytes[0] == '{'.code.toByte() || bytes[0] == '['.code.toByte()) {
            val text = String(bytes, Charsets.UTF_8)
            val resp = try {
                json.decodeFromString<WxaCodeResp>(text)
            } catch (e: Exception) {
                logger.warn("[Wechat]: 小程序码响应解析失败: {}", text.take(200))
                return null
            }
            logger.warn("[Wechat]: 小程序码生成失败 errcode={} errmsg={}", resp.errcode, resp.errmsg)
            return null
        }
        return bytes
    }

    /** 获取（必要时刷新）全局 access_token，协程锁保证并发下只刷新一次。 */
    private suspend fun getAccessToken(): String {
        cachedAccessToken?.let {
            if (System.currentTimeMillis() < tokenExpireAtMillis) return it
        }
        return tokenMutex.withLock {
            // double-check：等锁期间可能已被其他协程刷新
            cachedAccessToken?.let {
                if (System.currentTimeMillis() < tokenExpireAtMillis) return@withLock it
            }
            refreshAccessToken()
        }
    }

    private suspend fun refreshAccessToken(): String {
        val cfg = WechatConfig.miniApp
        val url = "$API_BASE/cgi-bin/token" +
            "?grant_type=client_credential&appid=${cfg.appId}&secret=${cfg.appSecret}"

        val text = try {
            client.get(url).bodyAsText()
        } catch (e: Exception) {
            throw WechatApiException("获取微信 access_token 网络失败: ${e.message}", e)
        }
        val resp = json.decodeFromString<AccessTokenResp>(text)
        val token = resp.accessToken
        if (token.isNullOrBlank()) {
            throw WechatApiException("获取微信 access_token 失败 errcode=${resp.errcode} errmsg=${resp.errmsg}")
        }
        // 提前 5 分钟过期，临界点用旧 token 换取的数据不会在处理途中失效
        val marginMillis = 5 * 60_000L
        tokenExpireAtMillis = System.currentTimeMillis() +
            ((resp.expiresIn ?: 7200L) * 1000L) - marginMillis
        cachedAccessToken = token
        logger.info("[Wechat]: access_token 已刷新，有效期 ${resp.expiresIn}s")
        return token
    }

    @Serializable
    private data class Code2SessionResp(
        val openid: String? = null,
        val unionid: String? = null,
        @kotlinx.serialization.SerialName("session_key") val sessionKey: String? = null,
        val errcode: Int? = null,
        val errmsg: String? = null
    )

    @Serializable
    private data class AccessTokenResp(
        @kotlinx.serialization.SerialName("access_token") val accessToken: String? = null,
        @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long? = null,
        val errcode: Int? = null,
        val errmsg: String? = null
    )

    @Serializable
    private data class PhoneNumberResp(
        val errcode: Int? = null,
        val errmsg: String? = null,
        @kotlinx.serialization.SerialName("phone_info") val phoneInfo: PhoneInfo? = null
    )

    @Serializable
    private data class PhoneInfo(
        // 微信该字段为驼峰 phoneNumber（不带下划线）
        val phoneNumber: String? = null,
        val purePhoneNumber: String? = null
    )

    @Serializable
    private data class SubscribeSendResp(
        val errcode: Int? = null,
        val errmsg: String? = null
    )

    @Serializable
    private data class WxaCodeResp(
        val errcode: Int? = null,
        val errmsg: String? = null
    )
}
