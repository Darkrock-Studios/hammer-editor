group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

buildscript {
    repositories {
        gradlePluginPortal()
		mavenCentral()
    }

    dependencies {
		classpath(libs.moko.resources.generator)
		classpath(libs.kotlinx.atomicfu.plugin)
		classpath(libs.parcelize.darwin)
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
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.moko.resources) apply false
	//alias(libs.plugins.parcelize.darwin) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
}

koverMerged {
	enable()
	filters {
		projects {
			excludes += listOf(":android", ":desktop", ":composeUi")
		}
	}
}