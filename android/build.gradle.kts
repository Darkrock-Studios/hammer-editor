val app_version: String by extra
//val androidx_compose_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val jetbrains_compose_version: String by extra
val jetpack_compose_compiler_version: String by extra

plugins {
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
}

group = "com.darkrockstudios.apps.hammer"
version = app_version

repositories {
    mavenCentral()
}

dependencies {
    api(project(":composeUi"))
    implementation("androidx.activity:activity-compose:1.6.1")
}

android {
    compileSdk = android_compile_sdk.toInt()
    defaultConfig {
        applicationId = "com.darkrockstudios.apps.hammer.android"
        minSdk = android_min_sdk.toInt()
        targetSdk = android_target_sdk.toInt()
        versionCode = 1
        versionName = "1.0-SNAPSHOT"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = jetpack_compose_compiler_version
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                File("proguard-rules.pro")
            )
        }
    }
}