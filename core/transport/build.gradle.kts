plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.simbridge.core.transport"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:protocol"))
    api(project(":core:crypto"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
