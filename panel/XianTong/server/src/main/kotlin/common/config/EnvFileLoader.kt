package com.dentalclinic.common.config

import org.slf4j.LoggerFactory
import java.io.File

/**
 * 本地 .env 文件加载（仅非生产环境）。
 *
 * 背景：`run-local.sh` 会 source `.env.local`，但在 IDE 里直接运行 EngineMain 时不会经过脚本，
 * 导致密钥缺失、能力降级为 mock。这里补齐这一场景，避免依赖 IDE 插件。
 *
 * 优先级与安全约定：
 * 1. 真实环境变量优先，文件只做补充，不会覆盖已注入的值；
 * 2. `APP_ENV=prod`（环境变量或文件内声明）时完全不读取，防止生产误加载本地凭证；
 * 3. 文件位于 `server/.env.local`，不在 resources 下，不会被打进 JAR。
 */
object EnvFileLoader {

    private val logger = LoggerFactory.getLogger("EnvFile")

    private const val FILE_NAME = ".env.local"

    /** 兼容三种常见工作目录：server/、panel/XianTong/、仓库根 */
    private val RELATIVE_PATHS = listOf(
        FILE_NAME,
        "server/$FILE_NAME",
        "panel/XianTong/server/$FILE_NAME",
    )

    private val values: Map<String, String> by lazy { load() }

    /** 环境变量优先，其次 .env.local；空白值视为未配置 */
    fun get(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            ?: values[name]?.trim()?.takeIf { it.isNotEmpty() }

    private fun load(): Map<String, String> {
        if (System.getenv("APP_ENV")?.trim() == "prod") return emptyMap()

        val file = candidates().firstOrNull { it.isFile } ?: return emptyMap()
        val parsed = file.readLines()
            .mapNotNull(::parseLine)
            .toMap()

        if (parsed["APP_ENV"]?.trim() == "prod") {
            logger.warn("[EnvFile]: {} 声明 APP_ENV=prod，已忽略该文件", file.path)
            return emptyMap()
        }

        logger.info("[EnvFile]: 已加载 {}（{} 项）", file.path, parsed.size)
        return parsed
    }

    private fun candidates(): List<File> {
        val cwd = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(cwd) { it.parentFile }
            .take(4)
            .flatMap { dir -> RELATIVE_PATHS.asSequence().map(dir::resolve) }
            .toList()
    }

    /** 支持 KEY=VALUE / export KEY=VALUE、忽略注释与空行、去掉成对引号 */
    private fun parseLine(raw: String): Pair<String, String>? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val index = line.indexOf('=')
        if (index <= 0) return null
        val key = line.substring(0, index).removePrefix("export ").trim()
        if (key.isEmpty()) return null
        val value = line.substring(index + 1).trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        return key to value
    }
}
