import com.darkrockstudios.build.configureRelease
import com.darkrockstudios.build.getVersionCode

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
		val version: String? = project.findProperty("version") as String?

		fun extractVersionChangelog(changelog: String, version: String): String {
			val regexPattern = "## \\[$version\\](.*?)--"
			val regex = Regex(regexPattern, RegexOption.DOT_MATCHES_ALL)
			val matchResult = regex.find(changelog)

			val versionChangelogWithHeader = matchResult?.value?.trim()?.removeSuffix("--")
				?: throw IllegalArgumentException("Version $version not found in changelog")

			// we don't want the top header with the version string in the fastlane file
			return versionChangelogWithHeader.lines()
				.drop(1) // Drop the first line (version string line)
				.joinToString(separator = "\n")
				.trim()
		}

		val releaseInfo = configureRelease(libs.versions.app.get())
		println(releaseInfo)

		println("Creating new release")
		val versionString = version ?: throw IllegalArgumentException("Version not provided")
		val versionCode = getVersionCode(versionString) // this will look for env vars
		val changelogFile = File("${project.rootDir}/CHANGELOG.md")
		val changelogText = changelogFile.readText()
		val versionChangelog = extractVersionChangelog(changelogText, versionString)

		val truncatedChangelog = if (versionChangelog.length > 500) {
			"${versionChangelog.take(450)}...and more"
		} else {
			versionChangelog
		}

		// Write the changelog file
		val rootDir: File = project.rootDir
		val changelogsPath = "fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
		val changeLogsDir = rootDir.resolve(changelogsPath)
		val changeLogFile = File(changeLogsDir, "$versionCode.txt")
		changeLogFile.writeText(truncatedChangelog)
		println("Changelog for version $versionString written to $changelogsPath/$versionCode.txt")

//		exec {
//			commandLine 'cmd', '/c', 'whoami'
//			standardOutput = stdout
//		}
//		commandLine("git", "add", changeLogsDir.absolutePath)
//		commandLine("git", "commit", "-m", "Added fastlane changelog for $versionString")
	}
}