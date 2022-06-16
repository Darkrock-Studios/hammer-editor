import org.jetbrains.compose.compose

val kotlin_version: String by extra
val coroutines_version: String by extra
val compose_version: String by extra
val decompose_version: String by extra
val koin_version: String by extra

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("kotlin-parcelize")
}

group = "com.darkrockstudios.apps.hammer"
version = "1.0-SNAPSHOT"

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
                transitiveExport = true
                export("com.arkivanov.decompose:decompose:$decompose_version")
                // This isn't working for some reason, once it is remove transitiveExport
                export("com.arkivanov.essenty:lifecycle:0.3.1")
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
                api("com.squareup.okio:okio:3.1.0")

                //implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
                //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
                //implementation("net.mamoe.yamlkt:yamlkt:0.10.2")
            }
        }
        val commonTest by getting {
            dependencies {
                //implementation(kotlin("test"))
                //implementation("io.insert-koin:koin-test:3.2.0")
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.4.2")
                api("androidx.core:core-ktx:1.8.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")
                api("io.insert-koin:koin-android:$koin_version")
            }
        }
        val iosMain by getting {
            dependencies {
                api("com.arkivanov.decompose:decompose:$decompose_version")
                api("com.arkivanov.essenty:lifecycle:0.3.1")
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
            }
        }
        val desktopTest by getting
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
