package com.dentalclinic.common.wx

import com.dentalclinic.common.config.EnvFileLoader
import com.dentalclinic.common.config.GlobalConfig.appConfig
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 微信能力配置（小程序登录 + 微信支付 V3）。
 *
 * 取值优先级：环境变量 > application.yaml > 空串。生产环境用环境变量注入密钥，
 * 避免凭证进配置文件/代码库；本地开发可在 yaml 里留空，相关能力自动降级为 mock：
 * - 小程序 appId/appSecret 为空：登录回落到 mock_openid（见 UserAuthService）；
 * - 支付任一必填项为空：充值下单直接模拟支付成功（见 RechargeService）。
 *
 * 注意：mock 仅用于本地联调，启动日志会以 WARN 显式提示；上线前必须配齐。
 */
object WechatConfig {

    private val logger = LoggerFactory.getLogger("WechatConfig")

    /** 小程序登录/用户能力配置。 */
    data class MiniApp(
        val appId: String,
        val appSecret: String,
        /**
         * 小程序码（getwxacodeunlimit）生成的目标版本：
         * release=线上版（默认，扫码即用）、trial=体验版、develop=开发版。
         * 开发/联调阶段设 trial/develop，正式投放必须 release。
         */
        val wxaEnvVersion: String
    ) {
        /** 两项都配齐才启用真实 code2session，任一项缺失都走 mock。 */
        val enabled: Boolean = appId.isNotBlank() && appSecret.isNotBlank()

        /** 小程序码环境版本白名单校验，非法值回落线上版。 */
        val safeWxaEnvVersion: String =
            if (wxaEnvVersion in listOf("release", "trial", "develop")) wxaEnvVersion else "release"
    }

    /**
     * 微信支付 V3 配置。
     *
     * @param mchId 商户号
     * @param apiV3Key APIv3 密钥（32 字节，回调解密用）
     * @param merchantSerial 商户证书序列号
     * @param privateKeyPem 商户私钥（apiclient_key.pem，PKCS#8）PEM 文本
     * @param notifyUrl 支付结果异步通知地址，必须是公网 HTTPS
     */
    data class Pay(
        val mchId: String,
        val apiV3Key: String,
        val merchantSerial: String,
        val privateKeyPem: String,
        val notifyUrl: String
    ) {
        val enabled: Boolean = mchId.isNotBlank() &&
            apiV3Key.length == 32 &&
            merchantSerial.isNotBlank() &&
            privateKeyPem.isNotBlank() &&
            notifyUrl.isNotBlank()
    }

    val miniApp: MiniApp by lazy {
        val appId = resolve("WECHAT_APP_ID", "wechat.miniApp.appId")
        val appSecret = resolve("WECHAT_APP_SECRET", "wechat.miniApp.appSecret")
        val wxaEnvVersion = resolve("WECHAT_WXA_ENV_VERSION", "wechat.miniApp.wxaEnvVersion")
            .ifBlank { "release" }
        val cfg = MiniApp(appId, appSecret, wxaEnvVersion)
        if (!cfg.enabled) {
            logger.warn(
                "[Wechat]: 未配置 wechat.miniApp.appId/appSecret（或环境变量 WECHAT_APP_ID/WECHAT_APP_SECRET），" +
                    "微信登录降级为 mock_openid，小程序码/推送等依赖凭证的能力同步降级，仅限本地开发，上线前必须配置真实凭证"
            )
        }
        cfg
    }

    val pay: Pay by lazy {
        val mchId = resolve("WECHAT_MCH_ID", "wechat.pay.mchId")
        val apiV3Key = resolve("WECHAT_API_V3_KEY", "wechat.pay.apiV3Key")
        val merchantSerial = resolve("WECHAT_MERCHANT_SERIAL", "wechat.pay.merchantSerialNumber")
        val notifyUrl = resolve("WECHAT_PAY_NOTIFY_URL", "wechat.pay.notifyUrl")

        // 私钥优先取环境变量里的 PEM 文本（兼容容器把换行写成字面量 \n），
        // 否则按 WECHAT_PRIVATE_KEY_PATH / yaml privateKeyPath 读取 apiclient_key.pem 文件。
        val privateKeyPem = resolve("WECHAT_PRIVATE_KEY", "wechat.pay.privateKey")
            .replace("\\n", "\n")
            .takeIf { it.isNotBlank() }
            ?: resolve("WECHAT_PRIVATE_KEY_PATH", "wechat.pay.privateKeyPath")
                .takeIf { it.isNotBlank() }
                ?.let { path ->
                    runCatching { File(path).readText(Charsets.UTF_8) }
                        .onFailure { logger.error("[Wechat]: 读取商户私钥文件失败: $path", it) }
                        .getOrNull()
                }
                .orEmpty()

        val cfg = Pay(mchId, apiV3Key, merchantSerial, privateKeyPem, notifyUrl)
        if (!cfg.enabled) {
            logger.warn(
                "[Wechat]: 微信支付配置不完整（mchId/apiV3Key/商户证书序列号/商户私钥/notifyUrl），" +
                    "充值将模拟支付成功，仅限本地开发，上线前必须配置完整支付参数"
            )
        }
        cfg
    }

    /** 环境变量（含 server/.env.local）优先，其次 yaml 配置路径；都没有返回空串（不抛异常，交由 enabled 判断降级）。 */
    private fun resolve(envName: String, yamlPath: String): String {
        EnvFileLoader.get(envName)?.let { return it }
        return appConfig.propertyOrNull(yamlPath)
            ?.getString()
            ?.trim()
            .orEmpty()
    }
}
