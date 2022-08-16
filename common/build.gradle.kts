import org.jetbrains.compose.compose

val app_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val kotlin_version: String by extra
val coroutines_version: String by extra
val kotlinx_serialization_version: String by extra
val compose_version: String by extra
val decompose_version: String by extra
val koin_version: String by extra
val okio_version: String by extra
val essenty_version: String by extra
val mockk_version: String by extra

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("kotlin-parcelize")
}

group = "com.darkrockstudios.apps.hammer"
version = app_version

kotlin {
    android()
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
    ios {
        binaries {
            framework {
                baseName = "Hammer"
                //transitiveExport = true
                export("com.arkivanov.decompose:decompose:$decompose_version")
                // This isn't working for some reason, once it is remove transitiveExport
                export("com.arkivanov.essenty:lifecycle:$essenty_version")
                export("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("com.arkivanov.decompose:decompose:$decompose_version")
                api("io.github.aakira:napier:2.6.1")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
                api("io.insert-koin:koin-core:$koin_version")
                api("com.squareup.okio:okio:$okio_version")

                api("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinx_serialization_version")
                // This is being held back to 0.3.2 due to ios support not working in later versions
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
                //api("com.akuleshov7:ktoml-core:0.2.12")
                //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
                //implementation("net.mamoe.yamlkt:yamlkt:0.10.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                //implementation("io.insert-koin:koin-test:$koin_version")
                implementation("com.squareup.okio:okio-fakefilesystem:$okio_version")
                implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.5.0")
                api("androidx.core:core-ktx:1.8.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")
                api("io.insert-koin:koin-android:$koin_version")
            }
        }
        val iosMain by getting {
            dependencies {
                api("com.arkivanov.decompose:decompose:$decompose_version")
                api("com.arkivanov.essenty:lifecycle:$essenty_version")
            }
        }
        val iosTest by getting
        val androidTest by getting {
            dependencies {
            }
        }
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
                api(compose.desktop.currentOs)
                api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinx_serialization_version")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation("io.mockk:mockk:$mockk_version")
            }
        }
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
