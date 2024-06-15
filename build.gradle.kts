import com.darkrockstudios.build.configureRelease
import com.darkrockstudios.build.writeChangelogMarkdown
import com.darkrockstudios.build.writeSemvar

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
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.moko.resources) apply false
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
}

dependencies {
	//kover(project(":base"))
	kover(project(":common"))
	kover(project(":server"))
}

kover {
	reports {
		total {

		}
	}
}

tasks.register("prepareForRelease") {
	doLast {
		val releaseInfo = configureRelease(libs.versions.app.get()) ?: error("Failed to configure new release")

		println("Creating new release")
		val versionCode = releaseInfo.semVar.createVersionCode(true, 0)

		// Write the new version number
		val versionsPath = "gradle/libs.versions.toml".replace("/", File.separator)
		val versionsFile = project.rootDir.resolve(versionsPath)
		writeSemvar(libs.versions.app.get(), releaseInfo.semVar, versionsFile)

		// Google Play has a hard limit of 500 characters
		val truncatedChangelog = if (releaseInfo.changeLog.length > 500) {
			"${releaseInfo.changeLog.take(480)}... and more"
		} else {
			releaseInfo.changeLog
		}

		// Write the Fastlane changelog file
		val rootDir: File = project.rootDir
		val changelogsPath = "fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
		val changeLogsDir = rootDir.resolve(changelogsPath)
		val changeLogFile = File(changeLogsDir, "$versionCode.txt")
		changeLogFile.writeText(truncatedChangelog)
		println("Changelog for version ${releaseInfo.semVar} written to $changelogsPath/$versionCode.txt")

		// Write the Global changelog file
		val globalChangelogFile = File("${project.rootDir}/CHANGELOG.md")
		writeChangelogMarkdown(releaseInfo, globalChangelogFile)

		// Commit the changes to the repo
		exec { commandLine = listOf("git", "add", changeLogFile.absolutePath) }
		exec { commandLine = listOf("git", "add", versionsFile.absolutePath) }
		exec { commandLine = listOf("git", "add", globalChangelogFile.absolutePath) }
		exec { commandLine = listOf("git", "commit", "-m", "Prepared for release: v${releaseInfo.semVar}") }

		// Merge develop into release
		exec { commandLine = listOf("git", "checkout", "release") }
		exec { commandLine = listOf("git", "merge", "develop") }

		// Create the release tag
		exec { commandLine = listOf("git", "tag", "-a", "v${releaseInfo.semVar}", "-m", releaseInfo.changeLog) }

		// Push and begin the release process
		exec { commandLine = listOf("git", "push", "origin", "--all") }
		exec { commandLine = listOf("git", "push", "origin", "--tags") }
	}
}

tasks.register("publishFdroid") {
	doLast {
		val releaseInfo = configureRelease(libs.versions.app.get()) ?: error("Failed to configure fdroid release")
		val versionCode = releaseInfo.semVar.createVersionCode(true, 0)
		exec { commandLine = listOf("git", "checkout", "release") }
		exec { commandLine = listOf("git", "tag", "-a", "fdroid-${versionCode}") }
		exec { commandLine = listOf("git", "push", "origin", "--tags") }
	}
}