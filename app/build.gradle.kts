plugins {
    alias(libs.plugins.android.application)
    // 不套 kotlin-android：AGP 9 內建 Kotlin
    alias(libs.plugins.compose)
}

android {
    namespace = "tw.avianjay.netshift"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "tw.avianjay.netshift"
        minSdk { version = release(30) }
        targetSdk { version = release(36) }
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Shizuku.newProcess 是用反射呼叫的；要開混淆請保留 proguard-rules.pro 裡的規則
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 內建 Kotlin 的 JVM target 由上面的 targetCompatibility 推得，不需要 kotlinOptions

    lint {
        // 版本是刻意釘住的（見 gradle/libs.versions.toml），不要被「有新版」洗版
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu.core)
}
