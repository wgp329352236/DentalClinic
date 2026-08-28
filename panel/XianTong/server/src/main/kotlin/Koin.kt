package com.dentalclinic

import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Koin dependency injection configuration.
 * Admin services are instantiated directly in adminRouting();
 * add Koin bindings here when needed for future modules.
 */
fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(module {
            // Placeholder — add service bindings as needed
        })
    }
}
