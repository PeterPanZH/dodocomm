// Top-level build file.
//
// AGP 9 has built-in Kotlin support (no kotlin-android plugin). We pin the Kotlin
// Gradle plugin to a recent stable release so the Compose compiler plugin version
// matches the compiler that AGP uses for built-in Kotlin.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
