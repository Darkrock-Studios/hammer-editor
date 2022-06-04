import org.jetbrains.compose.compose

val kotlin_version: String by extra
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("kotlin-parcelize")
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = "1.0-SNAPSHOT"

kotlin {
    android()
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                api("com.arkivanov.decompose:extensions-compose-jetbrains:0.6.0")
            }
        }
        val androidMain by getting
    }
}
android {
    compileSdk = 31
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 24
        targetSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}