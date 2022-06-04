plugins {
    kotlin("android")
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
    implementation("androidx.compose.ui:ui:1.1.1")
    implementation("androidx.compose.ui:ui-tooling:1.1.1")
    implementation("androidx.compose.foundation:foundation:1.1.1")
    implementation("androidx.compose.material:material:1.1.1")
    implementation("androidx.compose.material:material-icons-core:1.1.1")
    implementation("androidx.compose.material:material-icons-extended:1.1.1")
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
        }
    }
}