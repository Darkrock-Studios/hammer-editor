package com.darkrockstudios.build

import org.gradle.api.GradleException
import org.gradle.api.Project

private const val APPIMAGETOOL_VERSION = "1.9.1"
private const val APPIMAGE_RUNTIME_VERSION = "20251108"

/**
 * Checks if the current OS is Linux, throws a GradleException with helpful message if not.
 */
fun requireLinux(taskName: String) {
	val os = System.getProperty("os.name").lowercase()
	if (!os.contains("linux")) {
		throw GradleException(
			"""
			|
			|$taskName can only run on Linux.
			|Current OS: ${System.getProperty("os.name")}
			|
			|This task requires Linux-specific tools.
			|Options:
			|  - Run inside WSL2 on Windows
			|  - Run inside a Linux VM or container
			|  - Push to GitHub and let CI build it
			""".trimMargin()
		)
	}
}

/**
 * Registers the buildDistSnap task for building Snap packages.
 * Requires snapcraft to be installed on Linux.
 */
fun Project.registerBuildDistSnapTask(appVersion: String) {
	tasks.register("buildDistSnap") {
		group = "distribution"
		description = "Builds a Snap package for Linux distribution. Requires snapcraft to be installed."

		doFirst {
			requireLinux("buildDistSnap")
		}

		doLast {
			providers.exec {
				workingDir = rootDir
				commandLine("snapcraft")
			}.result.get()

			val snapFile = rootDir.resolve("hammer-editor_${appVersion}_amd64.snap")
			val outputDir = rootDir.resolve("desktop/build/installers/main-release/snap")
			outputDir.mkdirs()
			if (snapFile.exists()) {
				snapFile.copyTo(outputDir.resolve("hammer.snap"), overwrite = true)
				println("Snap package copied to: ${outputDir.resolve("hammer.snap")}")
			}
		}
	}
}

/**
 * Registers the buildDistAppImage task for building AppImage packages.
 * Requires wget to download appimagetool on Linux.
 */
fun Project.registerBuildDistAppImageTask() {
	tasks.register("buildDistAppImage") {
		group = "distribution"
		description = "Builds an AppImage for Linux distribution. Requires wget to download appimagetool."

		dependsOn(":desktop:createReleaseDistributable")

		doFirst {
			requireLinux("buildDistAppImage")
		}

		doLast {
			val appDir = rootDir.resolve("Hammer.AppDir")
			val outputDir = rootDir.resolve("desktop/build/installers/main-release/appimage")
			val appSourceDir = rootDir.resolve("desktop/build/installers/main-release/app/hammer")
			val iconFile = rootDir.resolve("desktop/icons/linux.png")
			val appimagetool = rootDir.resolve("appimagetool-x86_64.AppImage")
			val runtimeFile = rootDir.resolve("appimage-runtime-x86_64")

			// Clean previous build
			appDir.deleteRecursively()
			outputDir.mkdirs()

			// Download appimagetool if not present
			if (!appimagetool.exists()) {
				providers.exec {
					workingDir = rootDir
					commandLine(
						"wget", "-q", "-O", appimagetool.absolutePath,
						"https://github.com/AppImage/appimagetool/releases/download/$APPIMAGETOOL_VERSION/appimagetool-x86_64.AppImage"
					)
				}.result.get()
				providers.exec {
					commandLine("chmod", "+x", appimagetool.absolutePath)
				}.result.get()
			}

			// Download the AppImage runtime if not present. Passing it via --runtime-file
			// stops appimagetool from fetching the runtime from GitHub at pack time, which
			// removes a network dependency from the release build.
			if (!runtimeFile.exists()) {
				providers.exec {
					workingDir = rootDir
					commandLine(
						"wget", "-q", "-O", runtimeFile.absolutePath,
						"https://github.com/AppImage/type2-runtime/releases/download/$APPIMAGE_RUNTIME_VERSION/runtime-x86_64"
					)
				}.result.get()
			}

			// Create AppDir structure
			val usrDir = appDir.resolve("usr")
			val applicationsDir = appDir.resolve("usr/share/applications")
			val iconsDir = appDir.resolve("usr/share/icons/hicolor/256x256/apps")
			applicationsDir.mkdirs()
			iconsDir.mkdirs()

			// Copy the application
			providers.exec {
				commandLine("cp", "-r", "${appSourceDir.absolutePath}/.", usrDir.absolutePath)
			}.result.get()

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
			providers.exec {
				workingDir = appDir
				commandLine("ln", "-sf", "usr/share/applications/hammer.desktop", "hammer.desktop")
			}.result.get()
			providers.exec {
				workingDir = appDir
				commandLine("ln", "-sf", "usr/share/icons/hicolor/256x256/apps/hammer.png", "hammer.png")
			}.result.get()
			providers.exec {
				workingDir = appDir
				commandLine("ln", "-sf", "usr/bin/hammer", "AppRun")
			}.result.get()

			// Create the AppImage; -u embeds update info and emits hammer.AppImage.zsync for delta updates
			providers.exec {
				workingDir = rootDir
				environment("ARCH", "x86_64")
				commandLine(
					appimagetool.absolutePath,
					"--appimage-extract-and-run",
					"--runtime-file", runtimeFile.absolutePath,
					"-u", "gh-releases-zsync|Darkrock-Studios|hammer-editor|latest|hammer*.AppImage.zsync",
					appDir.absolutePath,
					outputDir.resolve("hammer.AppImage").absolutePath
				)
			}.result.get()

			val appImageFile = outputDir.resolve("hammer.AppImage")
			println("AppImage created: $appImageFile")

			// appimagetool picks zsyncmake off PATH, but --appimage-extract-and-run shadows
			// the system one with its bundled copy, which is broken (succeeds without writing
			// a file: AppImage/appimagetool#84). So generate the control file ourselves with
			// the system zsyncmake. URL is the AppImage basename, resolved relative to the
			// .zsync asset on the GitHub release.
			val zsyncFile = outputDir.resolve("hammer.AppImage.zsync")
			providers.exec {
				workingDir = outputDir
				commandLine(
					"zsyncmake",
					"-u", appImageFile.name,
					"-o", zsyncFile.absolutePath,
					appImageFile.absolutePath
				)
			}.result.get()

			if (!zsyncFile.exists()) {
				throw GradleException(
					"AppImage zsync file was not generated at $zsyncFile. " +
						"Ensure zsyncmake (the 'zsync' package) is installed and on PATH."
				)
			}
			println("AppImage zsync created: $zsyncFile")

			// Clean up AppDir
			appDir.deleteRecursively()
		}
	}
}

/**
 * Registers the buildDistFlatpak task for building Flatpak packages.
 * Requires flatpak and flatpak-builder to be installed on Linux.
 */
fun Project.registerBuildDistFlatpakTask() {
	tasks.register("buildDistFlatpak") {
		group = "distribution"
		description =
			"Builds a Flatpak package for Linux distribution. Requires flatpak and flatpak-builder to be installed."

		dependsOn(":desktop:createReleaseDistributable")

		doFirst {
			requireLinux("buildDistFlatpak")
		}

		doLast {
			val flatpakDir = rootDir.resolve("flatpak")
			val distDir = flatpakDir.resolve("dist")
			distDir.deleteRecursively()
			distDir.mkdirs()

			val appSourceDir = rootDir.resolve("desktop/build/installers/main-release/app/hammer")
			val hammerDistDir = distDir.resolve("hammer")
			hammerDistDir.mkdirs()

			// Copy the application
			copy {
				from(appSourceDir)
				into(hammerDistDir)
			}

			// Copy metadata
			copy {
				from(flatpakDir.resolve("studio.darkrock.hammer.desktop"))
				from(flatpakDir.resolve("studio.darkrock.hammer.metainfo.xml"))
				from(rootDir.resolve("desktop/icons/linux.png")) {
					rename { "studio.darkrock.hammer.png" }
				}
				into(distDir)
			}

			// Create launcher script
			val launcherScript = distDir.resolve("hammer.sh")
			launcherScript.writeText(
				"""
				#!/bin/bash
				export JAVA_HOME=/app/lib/hammer/runtime
				export PATH=${'$'}JAVA_HOME/bin:${'$'}PATH
				exec /app/lib/hammer/bin/hammer "${'$'}@"
				""".trimIndent()
			)
			launcherScript.setExecutable(true)

			val buildDir = rootDir.resolve("flatpak-build")
			val repoDir = rootDir.resolve("flatpak-repo")
			val outputDir = rootDir.resolve("desktop/build/installers/main-release/flatpak")
			val manifestFile = flatpakDir.resolve("ci-build.yaml")

			// Clean previous build artifacts
			buildDir.deleteRecursively()
			repoDir.deleteRecursively()
			outputDir.mkdirs()

			// Build the flatpak
			providers.exec {
				workingDir = rootDir
				commandLine(
					"flatpak-builder",
					"--user",
					"--repo=${repoDir.absolutePath}",
					"--force-clean",
					buildDir.absolutePath,
					manifestFile.absolutePath
				)
			}.result.get()

			// Create the bundle
			providers.exec {
				workingDir = rootDir
				commandLine(
					"flatpak",
					"build-bundle",
					repoDir.absolutePath,
					outputDir.resolve("hammer.flatpak").absolutePath,
					"studio.darkrock.hammer"
				)
			}.result.get()

			println("Flatpak package created at: ${outputDir.resolve("hammer.flatpak")}")

			// Clean up build directories and dist
			buildDir.deleteRecursively()
			repoDir.deleteRecursively()
			distDir.deleteRecursively()
		}
	}
}

/**
 * Registers all Linux distribution tasks (Snap, AppImage, Flatpak).
 */
fun Project.registerLinuxDistributionTasks(appVersion: String) {
	registerBuildDistSnapTask(appVersion)
	registerBuildDistAppImageTask()
	registerBuildDistFlatpakTask()
}
