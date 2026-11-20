package com.dentalclinic.common.config

import com.dentalclinic.common.sms.SmsConfig
import com.dentalclinic.common.wx.WechatConfig
import org.slf4j.LoggerFactory

/**
 * 启动配置预检（fail-fast 第一道防线）。
 *
 * 各配置对象本身是 lazy 解析的（缺密钥只在首次登录/首次签到时才暴露），本预检在进程启动阶段
 * 主动把所有关键配置一次性检查完：
 * - 生产环境（APP_ENV=prod）：存在任何缺失/弱默认值项 → 汇总后抛出 IllegalStateException，
 *   阻止进程启动，避免带病上线；
 * - 非生产环境：缺失项降级为 WARN 汇总，不影响本地启动（仍可走 mock/开发默认值）。
 *
 * 检查项：管理端 JWT、用户端 JWT、签到码 HMAC 密钥、数据库连接、微信小程序/支付配置、
 * 腾讯云短信配置、推荐分享域名。微信/短信相关检查复用各自 Config 的 enabled 判定（单一事实源）。
 */
object StartupPreflight {

    private val logger = LoggerFactory.getLogger("StartupPreflight")

    /** 各配置中残留的开发默认值，生产环境出现这些值视同未配置 */
    private val KNOWN_DEV_DEFAULTS = setOf(
        "admin-default-secret-change-me",
        "user-jwt-secret-change-me-in-production",
        "postgres",
        "change-me",
        "changeme",
        "phone-encryption-dev-key-change-me",
        "phone-blind-index-dev-key-change-me",
    )

    fun run() {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        checkSecret(
            label = "管理端 JWT 签名密钥",
            envName = "ADMIN_JWT_SECRET",
            yamlPath = "admin.jwt.secret",
            errors = errors,
            warnings = warnings,
        )
        checkSecret(
            label = "用户端 JWT 签名密钥",
            envName = "USER_JWT_SECRET",
            yamlPath = "user.jwt.secret",
            errors = errors,
            warnings = warnings,
        )
        checkSecret(
            label = "门店签到码 HMAC 密钥",
            envName = "CHECKIN_SIGNING_SECRET",
            yamlPath = "checkin.signingSecret",
            errors = errors,
            warnings = warnings,
        )
        checkSecret(
            label = "手机号加密密钥",
            envName = "PHONE_ENCRYPTION_KEY",
            yamlPath = "security.phoneEncryptionKey",
            errors = errors,
            warnings = warnings,
        )
        checkSecret(
            label = "手机号盲索引密钥",
            envName = "PHONE_BLIND_INDEX_KEY",
            yamlPath = "security.phoneBlindIndexKey",
            errors = errors,
            warnings = warnings,
        )

        checkDatabase(errors, warnings)
        checkWechat(errors, warnings)
        checkSms(errors, warnings)
        checkReferral(warnings)

        warnings.forEach { logger.warn("[Preflight] {}", it) }

        if (errors.isNotEmpty()) {
            errors.forEach { logger.error("[Preflight] 阻断启动：{}", it) }
            throw IllegalStateException(
                buildString {
                    appendLine("启动预检未通过（APP_ENV=${GlobalConfig.appEnv ?: "未设置"}），共 ${errors.size} 项阻断问题：")
                    errors.forEachIndexed { i, msg -> appendLine("  ${i + 1}. $msg") }
                    appendLine("请通过环境变量或 application-prod.yaml 补齐后重新启动。")
                }
            )
        }

        logger.info(
            "[Preflight] 启动预检通过（env={}，{} 项警告）",
            GlobalConfig.appEnv ?: "default",
            warnings.size
        )
    }

    /**
     * 密钥类配置统一检查：缺失/等于开发默认值 → 生产阻断、开发告警；
     * 长度不足 16 → 生产阻断（强度不足）；不足 32 → 所有环境告警。
     */
    private fun checkSecret(
        label: String,
        envName: String,
        yamlPath: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val value = GlobalConfig.resolveConfig(envName, yamlPath)
        val where = "环境变量 $envName 或配置项 $yamlPath"

        when {
            value == null -> {
                val msg = "$label 未配置（$where）"
                if (GlobalConfig.isProd) errors += "$msg，生产环境必须显式配置（建议 openssl rand -hex 32）"
                else warnings += "$msg，当前使用内置开发默认值/随机降级，仅限本地联调"
            }
            value in KNOWN_DEV_DEFAULTS -> {
                val msg = "$label 仍为开发默认值"
                if (GlobalConfig.isProd) errors += "$msg（$where），生产环境禁止使用默认密钥"
                else warnings += "$msg，仅限本地联调"
            }
            value.length < 16 -> {
                val msg = "$label 长度仅 ${value.length} 位，不足 16 位"
                if (GlobalConfig.isProd) errors += "$msg（$where），生产环境请使用至少 32 位随机密钥"
                else warnings += "$msg，强度偏弱，建议至少 32 位"
            }
            value.length < 32 -> {
                warnings += "$label 长度 ${value.length} 位，建议使用至少 32 位随机密钥（openssl rand -hex 32）"
            }
        }
    }

    private fun checkDatabase(errors: MutableList<String>, warnings: MutableList<String>) {
        val url = GlobalConfig.resolveConfig("DB_URL", "database.url")
        val username = GlobalConfig.resolveConfig("DB_USERNAME", "database.username")
        val password = GlobalConfig.resolveConfig("DB_PASSWORD", "database.password")

        if (url.isNullOrBlank()) {
            val msg = "数据库连接 URL 未配置（环境变量 DB_URL 或配置项 database.url）"
            if (GlobalConfig.isProd) errors += msg else errors += "$msg（无数据库任何环境都无法运行）"
        }
        if (username.isNullOrBlank()) {
            errors += "数据库用户名未配置（环境变量 DB_USERNAME 或配置项 database.username）"
        }

        when {
            password.isNullOrBlank() -> {
                val msg = "数据库密码未配置（环境变量 DB_PASSWORD 或配置项 database.password）"
                if (GlobalConfig.isProd) errors += msg else warnings += "$msg，本地依赖 yaml 默认值"
            }
            password == "postgres" -> {
                val msg = "数据库密码仍为示例弱口令 postgres"
                if (GlobalConfig.isProd) errors += "$msg（环境变量 DB_PASSWORD 或配置项 database.password），生产环境必须修改"
                else warnings += "$msg，仅限本地数据库使用"
            }
        }
    }

    /**
     * 复用 WechatConfig 的 enabled 判定（触发其 lazy 初始化与自身 WARN）：
     * 生产环境缺失微信能力意味着登录走 mock_openid、充值模拟支付成功，属于资损级风险，必须阻断。
     */
    private fun checkWechat(errors: MutableList<String>, warnings: MutableList<String>) {
        val miniApp = WechatConfig.miniApp
        if (!miniApp.enabled) {
            val msg = "微信小程序 appId/appSecret 未配置（WECHAT_APP_ID / WECHAT_APP_SECRET），登录将降级为 mock_openid"
            if (GlobalConfig.isProd) errors += "$msg，生产环境必须配置真实小程序凭证" else warnings += msg
        }

        val pay = WechatConfig.pay
        if (!pay.enabled) {
            val msg = "微信支付配置不完整（WECHAT_MCH_ID / WECHAT_API_V3_KEY / WECHAT_MERCHANT_SERIAL / WECHAT_PRIVATE_KEY(_PATH) / WECHAT_PAY_NOTIFY_URL），充值将模拟支付成功"
            if (GlobalConfig.isProd) errors += "$msg，生产环境必须配置完整支付参数，否则存在虚假入账资损风险"
            else warnings += msg
        } else if (!pay.notifyUrl.startsWith("https://")) {
            val msg = "微信支付回调地址不是 HTTPS：${pay.notifyUrl}"
            if (GlobalConfig.isProd) errors += "$msg（WECHAT_PAY_NOTIFY_URL），微信支付仅接受公网 HTTPS 回调"
            else warnings += msg
        }
    }

    /**
     * 短信验证码通道：生产缺失意味着用户收不到验证码、无法改手机号，阻断启动；
     * 本地缺失只打 WARN（验证码打印到服务端日志）。
     */
    private fun checkSms(errors: MutableList<String>, warnings: MutableList<String>) {
        if (SmsConfig.tencent.enabled) return
        val msg = "腾讯云短信配置不完整（TENCENT_SMS_SECRET_ID / TENCENT_SMS_SECRET_KEY / " +
            "TENCENT_SMS_SDK_APP_ID / TENCENT_SMS_SIGN_NAME / TENCENT_SMS_TEMPLATE_ID），验证码只打日志不真实下发"
        if (GlobalConfig.isProd) errors += "$msg，生产环境必须配置真实短信凭证"
        else warnings += msg
    }

    /** 推荐分享域名缺失只影响分享链接可用性，不阻断启动，所有环境告警 */
    private fun checkReferral(warnings: MutableList<String>) {
        val baseUrl = GlobalConfig.resolveConfig("REFERRAL_SHARE_BASE_URL", "referral.shareBaseUrl")
        if (baseUrl.isNullOrBlank() || baseUrl.contains("example.com")) {
            warnings += "推荐分享域名 referral.shareBaseUrl 未配置或仍为占位域名，邀请链接为死链，上线前必须替换"
        }
    }
}
