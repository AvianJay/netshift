// 根專案只宣告 plugin 版本。
// 刻意沒有 org.jetbrains.kotlin.android：AGP 9 內建 Kotlin，再套會出現
// "Cannot add extension with name 'kotlin'"。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
}
