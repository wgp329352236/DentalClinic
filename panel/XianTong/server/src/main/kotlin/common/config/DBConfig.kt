package com.dentalclinic.common.config

import com.dentalclinic.common.config.GlobalConfig.appConfig
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.koin.core.error.MissingPropertyException

object DBConfig {

    // 添加配置校验
    private fun ApplicationConfig.getRequiredString(path: String) =
        try {
            property(path).getString()
        } catch (_: MissingPropertyException) {
            throw IllegalStateException("Missing required configuration: $path")
        }

    /**
     * 数据库连接项解析：环境变量优先（DB_URL / DB_USERNAME / DB_PASSWORD），其次 yaml。
     * url/username 缺失时沿用原「配置缺失即启动失败」行为；
     * password 在生产环境（APP_ENV=prod）缺失时给出明确的 fail-fast 提示，
     * 避免生产环境静默使用弱口令/空口令。
     */
    val database: R2dbcDatabase by lazy {
        val url = GlobalConfig.resolveConfig("DB_URL", "database.url")
            ?: appConfig.getRequiredString("database.url")
        val user = GlobalConfig.resolveConfig("DB_USERNAME", "database.username")
            ?: appConfig.getRequiredString("database.username")
        val password = GlobalConfig.resolveConfig("DB_PASSWORD", "database.password")
            ?: if (GlobalConfig.isProd) {
                throw IllegalStateException(
                    "生产环境(APP_ENV=prod)缺少必需配置[数据库密码]：请设置环境变量 DB_PASSWORD 或配置项 database.password 后再启动"
                )
            } else {
                appConfig.getRequiredString("database.password")
            }

        R2dbcDatabase.connect(
            url = url,
            user = user,
            password = password
        )
    }

}