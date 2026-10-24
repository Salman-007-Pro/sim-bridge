plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.simbridge.core.testing"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:protocol"))
    api(project(":core:crypto"))
    implementation(project(":core:transport"))
    api(libs.bouncycastle.pkix)
    api(libs.kotlinx.coroutines.core)
}
