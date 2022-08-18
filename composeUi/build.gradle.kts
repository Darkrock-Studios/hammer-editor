import org.jetbrains.compose.compose

val app_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val kotlin_version: String by extra
val compose_version: String by extra
val decompose_version: String by extra
val mockk_version: String by extra
val okio_version: String by extra

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("kotlin-parcelize")
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = app_version

kotlin {
    android()
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(compose.runtime)
                api(compose.uiTooling)
                api(compose.preview)
                api(compose.foundation)
                api(compose.material)
                api(compose.animation)
                api(compose.animationGraphics)
                //@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                //api(compose.material3)
                api(compose.materialIconsExtended)
                api("org.jetbrains.compose.ui:ui-text:$compose_version")
                api("com.arkivanov.decompose:extensions-compose-jetbrains:$decompose_version")
                api("com.darkrockstudios:richtexteditor:1.3.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.squareup.okio:okio-fakefilesystem:$okio_version")
            }
        }
        val androidMain by getting
        val desktopMain by getting
    }
}
android {
    compileSdk = android_compile_sdk.toInt()
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res", "src/commonMain/resources")
    defaultConfig {
        minSdk = android_min_sdk.toInt()
        targetSdk = android_target_sdk.toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}