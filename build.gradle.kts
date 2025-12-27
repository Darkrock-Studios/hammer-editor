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
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.aboutlibraries.plugin.android) apply false
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
		val flatpakManifestPath = "flatpak/com.darkrockstudios.hammer.yaml".replace("/", File.separator)
		val flatpakManifestFile = project.rootDir.resolve(flatpakManifestPath)
		val flatpakMetainfoPath = "flatpak/com.darkrockstudios.hammer.metainfo.xml".replace("/", File.separator)
		val flatpakMetainfoFile = project.rootDir.resolve(flatpakMetainfoPath)
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

tasks.register("publishFdroid") {
	doLast {
		val semvarStr = libs.versions.app.get()
		val curSemVar = parseSemVar(semvarStr)
		val versionCode = curSemVar.createVersionCode(true, 0)
		val tag = "fdroid-${versionCode}"

		exec {
			commandLine = listOf(
				"git",
				"config",
				"--global",
				"user.email",
				"github-actions[bot]@users.noreply.github.com"
			)
		}
		exec {
			commandLine = listOf("git", "config", "--global", "user.name", "github-actions[bot]")
		}

		exec { commandLine = listOf("git", "fetch", "origin", "release") }
		exec { commandLine = listOf("git", "checkout", "release") }
		exec {
			commandLine = listOf(
				"git",
				"tag",
				"-a",
				tag,
				"-m",
				"FDroid release tag for $semvarStr"
			)
		}
		exec { commandLine = listOf("git", "push", "origin", "tag", tag) }
	}
}

tasks.register("buildDistSnap") {
	group = "distribution"
	description = "Builds a Snap package for Linux distribution. Requires snapcraft to be installed."

	doFirst {
		val os = System.getProperty("os.name").lowercase()
		if (!os.contains("linux")) {
			throw GradleException(
				"""
				|
				|buildDistSnap can only run on Linux.
				|Current OS: ${System.getProperty("os.name")}
				|
				|Snapcraft requires Linux with LXD container support.
				|Options:
				|  - Run inside WSL2 on Windows
				|  - Run inside a Linux VM or container
				|  - Push to GitHub and let CI build it
				""".trimMargin()
			)
		}
	}

	doLast {
		exec {
			workingDir = project.rootDir
			commandLine("snapcraft")
		}

		val version = libs.versions.app.get()
		val snapFile = project.rootDir.resolve("hammer-editor_${version}_amd64.snap")
		val outputDir = project.rootDir.resolve("desktop/build/installers/main/snap")
		outputDir.mkdirs()
		if (snapFile.exists()) {
			snapFile.copyTo(outputDir.resolve("hammer.snap"), overwrite = true)
			println("Snap package copied to: ${outputDir.resolve("hammer.snap")}")
		}
	}
}

tasks.register("buildDistAppImage") {
	group = "distribution"
	description = "Builds an AppImage for Linux distribution. Requires wget to download appimagetool."

	dependsOn(":desktop:createReleaseDistributable")

	doFirst {
		val os = System.getProperty("os.name").lowercase()
		if (!os.contains("linux")) {
			throw GradleException(
				"""
				|
				|buildDistAppImage can only run on Linux.
				|Current OS: ${System.getProperty("os.name")}
				|
				|AppImage packaging requires Linux.
				|Options:
				|  - Run inside WSL2 on Windows
				|  - Run inside a Linux VM or container
				|  - Push to GitHub and let CI build it
				""".trimMargin()
			)
		}
	}

	doLast {
		val appDir = project.rootDir.resolve("Hammer.AppDir")
		val outputDir = project.rootDir.resolve("desktop/build/installers/main-release/appimage")
		val appSourceDir = project.rootDir.resolve("desktop/build/installers/main-release/app/hammer")
		val iconFile = project.rootDir.resolve("desktop/icons/linux.png")
		val appimagetool = project.rootDir.resolve("appimagetool-x86_64.AppImage")

		// Clean previous build
		appDir.deleteRecursively()
		outputDir.mkdirs()

		// Download appimagetool if not present
		if (!appimagetool.exists()) {
			exec {
				workingDir = project.rootDir
				commandLine(
					"wget", "-q",
					"https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
				)
			}
			exec {
				commandLine("chmod", "+x", appimagetool.absolutePath)
			}
		}

		// Create AppDir structure
		val usrDir = appDir.resolve("usr")
		val applicationsDir = appDir.resolve("usr/share/applications")
		val iconsDir = appDir.resolve("usr/share/icons/hicolor/256x256/apps")
		applicationsDir.mkdirs()
		iconsDir.mkdirs()

		// Copy the application
		exec {
			commandLine("cp", "-r", "${appSourceDir.absolutePath}/.", usrDir.absolutePath)
		}

		// Create desktop file
		val desktopFile = applicationsDir.resolve("hammer.desktop")
		desktopFile.writeText(
			"""
			[Desktop Entry]
			Name=Hammer
			Comment=A simple tool for building stories
			Exec=hammer
			Icon=hammer
			Type=Application
			Categories=Office;TextEditor;
			""".trimIndent()
		)

		// Copy icon
		iconFile.copyTo(iconsDir.resolve("hammer.png"), overwrite = true)

		// Create symlinks at AppDir root (required by AppImage)
		exec {
			workingDir = appDir
			commandLine("ln", "-sf", "usr/share/applications/hammer.desktop", "hammer.desktop")
		}
		exec {
			workingDir = appDir
			commandLine("ln", "-sf", "usr/share/icons/hicolor/256x256/apps/hammer.png", "hammer.png")
		}
		exec {
			workingDir = appDir
			commandLine("ln", "-sf", "usr/bin/hammer", "AppRun")
		}

		// Create the AppImage
		exec {
			workingDir = project.rootDir
			environment("ARCH", "x86_64")
			commandLine(
				appimagetool.absolutePath,
				"--appimage-extract-and-run",
				appDir.absolutePath,
				outputDir.resolve("hammer.AppImage").absolutePath
			)
		}

		println("AppImage created at: ${outputDir.resolve("hammer.AppImage")}")

		// Clean up AppDir
		appDir.deleteRecursively()
	}
}

tasks.register("buildDistFlatpak") {
	group = "distribution"
	description =
		"Builds a Flatpak package for Linux distribution. Requires flatpak and flatpak-builder to be installed."

	doFirst {
		val os = System.getProperty("os.name").lowercase()
		if (!os.contains("linux")) {
			throw GradleException(
				"""
				|
				|buildDistFlatpak can only run on Linux.
				|Current OS: ${System.getProperty("os.name")}
				|
				|Flatpak and flatpak-builder are Linux-only tools.
				|Options:
				|  - Run inside WSL2 on Windows
				|  - Run inside a Linux VM or container
				|  - Push to GitHub and let CI build it
				""".trimMargin()
			)
		}
	}

	doLast {
		val buildDir = project.rootDir.resolve("flatpak-build")
		val repoDir = project.rootDir.resolve("flatpak-repo")
		val outputDir = project.rootDir.resolve("desktop/build/installers/main/flatpak")
		val manifestFile = project.rootDir.resolve("flatpak/com.darkrockstudios.hammer.yaml")

		// Clean previous build artifacts
		buildDir.deleteRecursively()
		repoDir.deleteRecursively()
		outputDir.mkdirs()

		// Build the flatpak (install dependencies from flathub)
		exec {
			workingDir = project.rootDir
			commandLine(
				"flatpak-builder",
				"--user",
				"--install-deps-from=flathub",
				"--force-clean",
				buildDir.absolutePath,
				manifestFile.absolutePath
			)
		}

		// Build again to create the repo
		exec {
			workingDir = project.rootDir
			commandLine(
				"flatpak-builder",
				"--user",
				"--repo=${repoDir.absolutePath}",
				"--force-clean",
				buildDir.absolutePath,
				manifestFile.absolutePath
			)
		}

		// Create the bundle
		exec {
			workingDir = project.rootDir
			commandLine(
				"flatpak",
				"build-bundle",
				repoDir.absolutePath,
				outputDir.resolve("hammer.flatpak").absolutePath,
				"com.darkrockstudios.hammer"
			)
		}

		println("Flatpak package created at: ${outputDir.resolve("hammer.flatpak")}")

		// Clean up build directories
		buildDir.deleteRecursively()
		repoDir.deleteRecursively()
	}
}