import org.jetbrains.compose.compose

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
                export("com.arkivanov.decompose:decompose:0.6.0")
                // This isn't working for some reason, once it is remove transitiveExport
                //export("com.arkivanov.essenty:lifecycle:0.3.1")
                export("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("com.arkivanov.decompose:decompose:0.6.0")
                api("io.github.aakira:napier:2.6.1")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                api("androidx.appcompat:appcompat:1.4.2")
                api("androidx.core:core-ktx:1.8.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
            }
        }
        val iosMain by getting
        val iosTest by getting
        val androidTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
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
