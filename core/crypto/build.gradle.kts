plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.simbridge.core.crypto"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.bouncycastle.pkix)
}
