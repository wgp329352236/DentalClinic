
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}


application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor 服务器核心与功能模块
    implementation(libs.ktor.server.core)                 // Ktor 服务器核心
    implementation(libs.ktor.server.cors)                // 跨域资源共享
    implementation(libs.ktor.server.netty)                // Netty 引擎
    implementation(libs.ktor.server.content.negotiation)  // 内容协商（JSON 序列化/反序列化）
    implementation(libs.ktor.serialization.kotlinx.json)  // kotlinx.serialization JSON 支持
    implementation(libs.ktor.server.config.yaml)          // YAML 配置文件支持

    // Ktor 认证模块
    implementation(libs.ktor.server.auth)                 // 认证基础框架
    implementation(libs.ktor.server.auth.jwt)             // JWT 认证支持

    // 数据库（Exposed ORM + R2DBC 响应式驱动）
    implementation(libs.exposed.core)                     // Exposed ORM 核心
    implementation(libs.exposed.java.time)               // java.time 列类型支持
    implementation(libs.exposed.json)                    // JSON 列类型支持

    implementation(libs.exposed.r2dbc)                    // Exposed R2DBC 异步驱动支持
    implementation(libs.postgresql.r2dbc)                 // PostgreSQL R2DBC 驱动


    // Ktor HTTP 客户端（飞书 API 调用等）
    implementation(libs.ktor.client.core)                 // HTTP 客户端核心
    implementation(libs.ktor.client.cio)                  // CIO 异步引擎

    // ─── Client: 管理后台 ───
    implementation(libs.ktor.server.status.pages)        // 统一异常处理 / 错误页
    implementation(libs.bcrypt)                          // 管理员密码哈希
    implementation(libs.lettuce.core)                    // Redis 异步客户端 (会话/缓存)

    // 依赖注入与日志
    implementation(libs.koin.ktor)                        // Koin 依赖注入 Ktor 集成
    implementation(libs.koin.loggerSlf4j)                 // Koin SLF4J 日志桥接
    implementation(libs.logback.classic)                  // Logback 日志实现

    // 测试依赖
    testImplementation(kotlin("test"))                    // Kotlin 测试基础
    testImplementation(libs.ktor.server.test.host)        // Ktor 服务器测试工具
}
