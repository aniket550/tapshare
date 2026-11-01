// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the kotlin-android plugin is NOT applied.
// AGP only brings an older Kotlin Gradle Plugin; putting the newer one on the classpath upgrades it
// (documented in the AGP 9.0 release notes).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
