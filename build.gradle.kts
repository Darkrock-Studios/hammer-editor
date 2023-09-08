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
		classpath(libs.jetbrains.kover)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
	// TODO Re-add "apply false" once this is fixed: https://youtrack.jetbrains.com/issue/KTIJ-25236
	alias(libs.plugins.kotlin.jvm) // apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.moko.resources) apply false
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.parcelize.darwin) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
}

dependencies {
	kover(project(":base"))
	kover(project(":common"))
	kover(project(":server"))
}

koverReport {
	defaults {

	}
}