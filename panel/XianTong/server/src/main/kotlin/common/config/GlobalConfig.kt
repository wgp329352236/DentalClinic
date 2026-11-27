package com.dentalclinic.common.config

import io.ktor.server.config.*
import org.slf4j.LoggerFactory

object GlobalConfig {

    private val logger = LoggerFactory.getLogger("GlobalConfig")

    /** 运行环境：local / prod；未设置时按本地默认配置运行 */
    val appEnv: String?
        get() = EnvFileLoader.get("APP_ENV")

    val isDebug: Boolean
        get() = appEnv == "dev"

    /** 生产环境标记：仅 APP_ENV=prod 时为 true，用于强制校验密钥等启动前置条件 */
    val isProd: Boolean
        get() = appEnv == "prod"

    private val configPath: String
        get() = when (appEnv) {
            "local", "dev" -> "application-local.yaml"
            "prod" -> "application-prod.yaml"
            else -> "application.yaml"
        }

    // 延迟加载配置
    val appConfig: ApplicationConfig by lazy {
        ApplicationConfig(configPath)
    }

    /**
     * 通用配置/密钥解析：环境变量优先，其次 yaml 配置路径；
     * 两侧都缺失或为空白时返回 null。调用方自行决定降级（mock）还是 fail-fast。
     */
    fun resolveConfig(envName: String, yamlPath: String): String? =
        EnvFileLoader.get(envName)
            ?: appConfig.propertyOrNull(yamlPath)
                ?.getString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

    /**
     * 必需密钥解析：环境变量 > yaml。
     * - 生产环境（APP_ENV=prod）缺失时直接抛 IllegalStateException 阻止启动（fail-fast），
     *   避免带默认弱密钥上线；
     * - 非生产环境缺失时回退 [devDefault] 并打 WARN，仅限本地联调使用。
     *
     * @param envName  环境变量名，如 USER_JWT_SECRET
     * @param yamlPath yaml 配置路径，如 user.jwt.secret
     * @param devDefault 非生产环境的开发默认值（禁止在生产使用）
     * @param label    日志/异常中展示的密钥中文名
     */
    fun requireSecret(
        envName: String,
        yamlPath: String,
        devDefault: String,
        label: String
    ): String {
        resolveConfig(envName, yamlPath)?.let { return it }
        if (isProd) {
            throw IllegalStateException(
                "生产环境(APP_ENV=prod)缺少必需配置[$label]：请设置环境变量 $envName 或配置项 $yamlPath 后再启动"
            )
        }
        logger.warn(
            "[Config]: 未配置{}（环境变量 {} / 配置项 {}），使用内置开发默认值，仅限本地联调，上线前必须配置",
            label, envName, yamlPath
        )
        return devDefault
    }
}
