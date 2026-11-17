package com.dentalclinic.common.sms

import com.dentalclinic.common.config.GlobalConfig
import org.slf4j.LoggerFactory

/**
 * 短信验证码配置（腾讯云短信 SendSms）。
 *
 * 取值优先级与 WechatConfig 保持一致：环境变量 > application.yaml > 空串。
 * 任一项缺失即视为未配置，[SmsVerificationService] 回落为「验证码只打日志」的 mock 分支，
 * 仅限本地联调；`APP_ENV=prod` 时由 StartupPreflight 阻断启动，避免线上收不到验证码。
 */
object SmsConfig {

    private val logger = LoggerFactory.getLogger("SmsConfig")

    /**
     * @param secretId 腾讯云 API 密钥 SecretId
     * @param secretKey 腾讯云 API 密钥 SecretKey
     * @param sdkAppId 短信应用 SdkAppId（1400 开头）
     * @param signName 已审核通过的短信签名内容（不含【】）
     * @param templateId 已审核通过的正文模板 ID
     * @param region 接口地域，默认 ap-guangzhou
     */
    data class Tencent(
        val secretId: String,
        val secretKey: String,
        val sdkAppId: String,
        val signName: String,
        val templateId: String,
        val region: String
    ) {
        /** 五项凭证都配齐才真实发送，否则降级为日志 mock。 */
        val enabled: Boolean = secretId.isNotBlank() &&
            secretKey.isNotBlank() &&
            sdkAppId.isNotBlank() &&
            signName.isNotBlank() &&
            templateId.isNotBlank()
    }

    val tencent: Tencent by lazy {
        val cfg = Tencent(
            secretId = resolve("TENCENT_SMS_SECRET_ID", "sms.tencent.secretId"),
            secretKey = resolve("TENCENT_SMS_SECRET_KEY", "sms.tencent.secretKey"),
            sdkAppId = resolve("TENCENT_SMS_SDK_APP_ID", "sms.tencent.sdkAppId"),
            signName = resolve("TENCENT_SMS_SIGN_NAME", "sms.tencent.signName"),
            templateId = resolve("TENCENT_SMS_TEMPLATE_ID", "sms.tencent.templateId"),
            region = resolve("TENCENT_SMS_REGION", "sms.tencent.region").ifBlank { "ap-guangzhou" }
        )
        if (!cfg.enabled) {
            logger.warn(
                "[SMS]: 未配置腾讯云短信凭证（TENCENT_SMS_SECRET_ID/SECRET_KEY/SDK_APP_ID/SIGN_NAME/TEMPLATE_ID），" +
                    "验证码只打印服务端日志、不会真实下发，仅限本地开发，上线前必须配置"
            )
        }
        cfg
    }

    /** 环境变量优先，其次 yaml 配置路径；都没有返回空串（交由 enabled 判断降级）。 */
    private fun resolve(envName: String, yamlPath: String): String =
        GlobalConfig.resolveConfig(envName, yamlPath).orEmpty()
}
