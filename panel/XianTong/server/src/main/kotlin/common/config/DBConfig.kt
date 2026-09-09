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

    val database: R2dbcDatabase by lazy {
        R2dbcDatabase.connect(
            url = appConfig.getRequiredString("database.url"),
            user = appConfig.getRequiredString("database.username"),
            password = appConfig.getRequiredString("database.password")
        )
    }

}