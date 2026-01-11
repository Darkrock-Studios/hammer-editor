package com.darkrockstudios.build

import org.gradle.api.Project
import java.io.File

fun Project.registerPrepareForReleaseTask() {
	tasks.register("prepareForRelease") {
		doLast {
			val libs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
			val releaseInfo =
				configureRelease(libs.findVersion("app").get().toString()) ?: error("Failed to configure new release")

			println("Creating new release")
			val versionCode = releaseInfo.semVar.createVersionCode(true, 0)

			// Write the new version number
			val versionsPath = "gradle/libs.versions.toml".replace("/", File.separator)
			val versionsFile = rootDir.resolve(versionsPath)
			writeSemvar(libs.findVersion("app").get().toString(), releaseInfo.semVar, versionsFile)

			// Google Play has a hard limit of 500 characters
			val truncatedChangelog = if (releaseInfo.changeLog.length > 500) {
				"${releaseInfo.changeLog.take(480)}... and more"
			} else {
				releaseInfo.changeLog
			}

			// Write the Fastlane changelog file
			val changelogsPath =
				"fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
			val changeLogsDir = rootDir.resolve(changelogsPath)
			val changeLogFile = File(changeLogsDir, "$versionCode.txt")
			changeLogFile.writeText(truncatedChangelog)
			println("Changelog for version ${releaseInfo.semVar} written to $changelogsPath/$versionCode.txt")

			// Write the Global changelog file
			val globalChangelogFile = File("${rootDir}/CHANGELOG.md")
			writeChangelogMarkdown(releaseInfo, globalChangelogFile)

			// Update snapcraft.yaml with new version and JVM version
			val snapcraftPath = "snap/snapcraft.yaml".replace("/", File.separator)
			val snapcraftFile = rootDir.resolve(snapcraftPath)
			val jvmVersion = libs.findVersion("jvm").get().toString()
			updateSnapcraftYaml(releaseInfo.semVar, jvmVersion, snapcraftFile)

			// Update Flatpak manifest and metainfo with new version and JVM version
			val flatpakManifestPath = "flatpak/com.darkrockstudios.hammer.yaml".replace("/", File.separator)
			val flatpakManifestFile = rootDir.resolve(flatpakManifestPath)
			val flatpakMetainfoPath = "flatpak/com.darkrockstudios.hammer.metainfo.xml".replace("/", File.separator)
			val flatpakMetainfoFile = rootDir.resolve(flatpakMetainfoPath)
			updateFlatpakFiles(releaseInfo.semVar, jvmVersion, flatpakManifestFile, flatpakMetainfoFile)

			// Commit the changes to the repo
			exec { commandLine = listOf("git", "add", changeLogFile.absolutePath) }
			exec { commandLine = listOf("git", "add", versionsFile.absolutePath) }
			exec { commandLine = listOf("git", "add", globalChangelogFile.absolutePath) }
			exec { commandLine = listOf("git", "add", snapcraftFile.absolutePath) }
			exec { commandLine = listOf("git", "add", flatpakManifestFile.absolutePath) }
			exec { commandLine = listOf("git", "add", flatpakMetainfoFile.absolutePath) }
			exec {
				commandLine =
					listOf("git", "commit", "-m", "Prepared for release: v${releaseInfo.semVar}")
			}

			// Merge develop into release
			exec { commandLine = listOf("git", "checkout", "release") }
			exec { commandLine = listOf("git", "merge", "develop") }

			// Create the release tag
			exec {
				commandLine =
					listOf("git", "tag", "-a", "v${releaseInfo.semVar}", "-m", releaseInfo.changeLog)
			}

			// Push and begin the release process
			exec { commandLine = listOf("git", "push", "origin", "--all") }
			exec { commandLine = listOf("git", "push", "origin", "--tags") }

			// Leave the repo back on develop
			exec { commandLine = listOf("git", "checkout", "develop") }
		}
	}
}
