import com.darkrockstudios.build.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

buildscript {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}

	dependencies {
		classpath(libs.kotlinx.atomicfu.plugin)
		classpath(libs.jetbrains.kover)
	}
}

val xlibs = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
	repositories {
		google()
		mavenCentral()
		maven("https://jitpack.io")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}

	// Compiler flags applied globally
	tasks.withType<KotlinCompilationTask<*>>().configureEach {
		compilerOptions {
			freeCompilerArgs.addAll(
				listOf(
					"-Xexpect-actual-classes",
					"-opt-in=kotlin.time.ExperimentalTime",
					"-opt-in=androidx.compose.material.ExperimentalMaterialApi",
					"-opt-in=androidx.compose.material3.ExperimentalMaterialApi",
					"-opt-in=androidx.compose.runtime.ExperimentalComposeApi",
					"-opt-in=com.arkivanov.decompose.ExperimentalDecomposeApi",
					"-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
				)
			)
		}
	}

	dependencies {
		enforcedPlatform(xlibs.findLibrary("junit.bom").get())
	}
}

plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.kotlin.multiplatform.library) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.compose.report.generator) apply false
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.aboutlibraries.plugin.android) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
	alias(libs.plugins.flatpak.gradle.generator) apply false
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

registerPublishTasks()
registerLinuxDistributionTasks(libs.versions.app.get())

val releasePreFlightChecks = tasks.register("releasePreFlightChecks") {
	doLast {
		println("Fetching origin...")
		providers.exec { commandLine = listOf("git", "fetch", "origin") }.result.get()

		// Check for unstaged/uncommitted changes
		val statusText = providers.exec {
			commandLine = listOf("git", "status", "--porcelain")
		}.standardOutput.asText.get().trim()
		if (statusText.isNotEmpty()) {
			error(
				"Working tree has uncommitted changes. Please commit or stash them before preparing a release.\n$statusText"
			)
		}

		// Check if develop is behind origin/develop
		val developBehind = providers.exec {
			commandLine = listOf("git", "rev-list", "--count", "develop..origin/develop")
		}.standardOutput.asText.get().trim().toIntOrNull() ?: 0
		if (developBehind > 0) {
			error("Local 'develop' is behind 'origin/develop' by $developBehind commit(s). Please pull before preparing a release.")
		}

		println("Pre-flight checks passed.")
	}
}

// Ensure flatpak generator runs after pre-flight checks
project(":desktop").tasks.configureEach {
	if (name == "flatpakGradleGenerator") {
		mustRunAfter(releasePreFlightChecks)
	}
}

tasks.register("prepareForRelease") {
	dependsOn(releasePreFlightChecks, ":desktop:flatpakGradleGenerator")
	doLast {
		val releaseInfo =
			configureRelease(libs.versions.app.get()) ?: error("Failed to configure new release")

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
		val changelogsPath =
			"fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
		val changeLogsDir = rootDir.resolve(changelogsPath)
		val changeLogFile = File(changeLogsDir, "$versionCode.txt")
		changeLogFile.writeText(truncatedChangelog)
		println("Changelog for version ${releaseInfo.semVar} written to $changelogsPath/$versionCode.txt")

		// Write the Global changelog file
		val globalChangelogFile = File("${project.rootDir}/CHANGELOG.md")
		writeChangelogMarkdown(releaseInfo, globalChangelogFile)

		// Update snapcraft.yaml with new version and JVM version
		val snapcraftPath = "snap/snapcraft.yaml".replace("/", File.separator)
		val snapcraftFile = project.rootDir.resolve(snapcraftPath)
		val jvmVersion = libs.versions.jvm.get()
		updateSnapcraftYaml(releaseInfo.semVar, jvmVersion, snapcraftFile)

		// Update Flatpak manifest and metainfo with new version and JVM version
		val flatpakManifestPath = "flatpak/studio.darkrock.hammer.yaml".replace("/", File.separator)
		val flatpakManifestFile = project.rootDir.resolve(flatpakManifestPath)
		val flatpakMetainfoPath = "flatpak/studio.darkrock.hammer.metainfo.xml".replace("/", File.separator)
		val flatpakMetainfoFile = project.rootDir.resolve(flatpakMetainfoPath)
		updateFlatpakFiles(releaseInfo.semVar, jvmVersion, flatpakManifestFile, flatpakMetainfoFile, releaseInfo.changeLog)

		// Commit the changes to the repo
		providers.exec { commandLine = listOf("git", "add", changeLogFile.absolutePath) }.result.get()
		providers.exec { commandLine = listOf("git", "add", versionsFile.absolutePath) }.result.get()
		providers.exec { commandLine = listOf("git", "add", globalChangelogFile.absolutePath) }.result.get()
		providers.exec { commandLine = listOf("git", "add", snapcraftFile.absolutePath) }.result.get()
		providers.exec { commandLine = listOf("git", "add", flatpakManifestFile.absolutePath) }.result.get()
		providers.exec { commandLine = listOf("git", "add", flatpakMetainfoFile.absolutePath) }.result.get()
		val flatpakSourcesPath = "flatpak/flatpak-sources.json".replace("/", File.separator)
		val flatpakSourcesFile = project.rootDir.resolve(flatpakSourcesPath)
		providers.exec { commandLine = listOf("git", "add", flatpakSourcesFile.absolutePath) }.result.get()
		providers.exec {
			commandLine =
				listOf("git", "commit", "-m", "Prepared for release: v${releaseInfo.semVar}")
		}.result.get()

		// Switch to release and reset to origin/release HEAD
		providers.exec { commandLine = listOf("git", "checkout", "release") }.result.get()
		providers.exec { commandLine = listOf("git", "reset", "--hard", "origin/release") }.result.get()
		providers.exec { commandLine = listOf("git", "merge", "develop") }.result.get()

		// Create the release tag
		providers.exec {
			commandLine =
				listOf("git", "tag", "-a", "v${releaseInfo.semVar}", "-m", releaseInfo.changeLog)
		}.result.get()

		// Push and begin the release process
		providers.exec { commandLine = listOf("git", "push", "origin", "--all") }.result.get()
		providers.exec { commandLine = listOf("git", "push", "origin", "--tags") }.result.get()

		// Leave the repo back on develop
		providers.exec { commandLine = listOf("git", "checkout", "develop") }.result.get()
	}
}
