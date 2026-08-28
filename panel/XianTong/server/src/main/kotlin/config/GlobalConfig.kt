package com.dentalclinic.config

import io.ktor.server.config.*

object GlobalConfig {

    val isDebug: Boolean
        get() {
            val env = System.getenv("APP_ENV") // 从环境变量中读取 APP_ENV 的值
            return when (env) {
                "dev" -> true
                else -> false
            }
        }

    private val configPath: String
        get() {
            val env = System.getenv("APP_ENV") // 从环境变量中读取 APP_ENV 的值
            return when (env) {
                "dev" -> "application-dev.yaml"
                "prod" -> "application-prod.yaml"
                else -> "application.yaml"
            }
        }

    // 延迟加载配置
    val appConfig: ApplicationConfig by lazy {
        ApplicationConfig(configPath)
    }
}