group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

buildscript {
    repositories {
        gradlePluginPortal()
    }

    dependencies {
		classpath(libs.moko.resources.generator)
		classpath(libs.kotlinx.atomicfu.plugin)
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
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.jetbrains.kover)
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