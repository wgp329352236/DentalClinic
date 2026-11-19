
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.rpc.grpc) apply false
}

subprojects {
    group = "com.dentalclinic"
    version = "1.0.0-SNAPSHOT"
}
