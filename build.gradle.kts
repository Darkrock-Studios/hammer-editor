val app_version: String by extra

group = "com.darkrockstudios.apps.hammer"
version = app_version

buildscript {
    repositories {
        gradlePluginPortal()
    }

    val moko_resources_version: String by extra
	val atomicfu_version: String by extra
    dependencies {
		classpath("dev.icerock.moko:resources-generator:$moko_resources_version")
		classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
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
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	id("org.jetbrains.compose") apply false
	id("org.jetbrains.kotlinx.kover") version "0.6.1"
	id("com.github.gmazzo.buildconfig") version "4.0.2" apply false
}

apply(plugin = "kotlinx-atomicfu")

koverMerged {
	enable()
	filters {
		projects {
			excludes += listOf(":android", ":desktop", ":composeUi")
		}
	}
}