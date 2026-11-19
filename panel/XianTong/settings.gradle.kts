pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/kxrpc-grpc")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/kxrpc-grpc")
    }
}

rootProject.name = "xiantong"

include(":client")
include(":server")
