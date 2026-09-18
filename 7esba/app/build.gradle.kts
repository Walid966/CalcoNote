plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "com.hesba.nativeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.walid966.twa"
        minSdk = 23
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt()
        versionName = "3.0.0"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".nativepreview"
            versionNameSuffix = "-preview"
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")) }
