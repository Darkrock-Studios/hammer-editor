import com.darkrockstudios.build.getVersionCode
import java.util.*

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
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.parcelize.darwin) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
}

dependencies {
	//kover(project(":base"))
	kover(project(":common"))
	kover(project(":server"))
}

koverReport {
	defaults {

	}
}

tasks.register("prepareForRelease") {
	doLast {
		println("Creating new release")
		println("Please enter new SemVar:")
		val scanner = Scanner(System.`in`)
		val semVarStr = scanner.nextLine()

		val versionCode = getVersionCode(semVarStr)

		println("Please enter new ChangeLog:")
		// TODO Need a way to read a whole block of text here
		val changeLog = scanner.nextLine()

		// Write the changelog file
		val rootDir: File = project.rootDir
		val changelogsPath = "fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
		val changeLogsDir = rootDir.resolve(changelogsPath)
		val changeLogFile = File(changeLogsDir, "$versionCode.txt")
		changeLogFile.writeText(changeLog!!)
	}
}