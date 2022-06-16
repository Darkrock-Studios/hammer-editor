val androidx_compose_version: String by extra

plugins {
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
}

group "com.darkrockstudios.apps.hammer"
version "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":composeUi"))
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.compose.ui:ui:$androidx_compose_version")
    implementation("androidx.compose.ui:ui-tooling:$androidx_compose_version")
    implementation("androidx.compose.foundation:foundation:$androidx_compose_version")
    implementation("androidx.compose.material:material:$androidx_compose_version")
    implementation("androidx.compose.material:material-icons-core:$androidx_compose_version")
    implementation("androidx.compose.material:material-icons-extended:$androidx_compose_version")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "com.darkrockstudios.apps.hammer.android"
        minSdk = 24
        targetSdk = 32
        versionCode = 1
        versionName = "1.0-SNAPSHOT"
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