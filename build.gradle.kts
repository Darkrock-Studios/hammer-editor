val app_version: String by extra

group = "com.darkrockstudios.apps.hammer"
version = app_version

buildscript {
    repositories {
        gradlePluginPortal()
    }

    dependencies {
        classpath("dev.icerock.moko:resources-generator:0.20.1")
    }
}


allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
}

plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.serialization") apply false
    kotlin("android") apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.compose") apply false
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("dev.icerock.mobile.multiplatform-resources") version "0.20.1"
}

koverMerged {
    enable()
    filters {
        projects {
            excludes += listOf(":android", ":desktop", ":composeUi")
        }
    }
}