package com.dentalclinic

import io.ktor.server.application.*

/**
 * Security configuration placeholder.
 * Admin module uses its own JwtAuthInterceptor; this function is kept
 * for application.yaml compatibility but no longer installs Ktor's
 * built-in JWT authentication.
 */
fun Application.configureSecurity() {
    // Intentionally empty — admin auth is handled by AdminModule
}
