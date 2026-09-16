package com.dentalclinic

import com.dentalclinic.common.config.StartupPreflight
import io.ktor.server.application.*

/**
 * 启动预检模块：在所有业务模块装载前执行关键配置校验。
 * 生产环境（APP_ENV=prod）存在缺失/弱默认密钥时直接抛异常终止启动（fail-fast）。
 */
fun Application.configurePreflight() {
    StartupPreflight.run()
}
