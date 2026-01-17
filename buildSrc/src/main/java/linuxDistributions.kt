package com.darkrockstudios.build

import org.gradle.api.GradleException
import org.gradle.api.Project

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

			// Clean previous build
			appDir.deleteRecursively()
			outputDir.mkdirs()

			// Download appimagetool if not present
			if (!appimagetool.exists()) {
				providers.exec {
					workingDir = rootDir
					commandLine(
						"wget", "-q",
						"https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
					)
				}.result.get()
				providers.exec {
					commandLine("chmod", "+x", appimagetool.absolutePath)
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

			// Create the AppImage
			providers.exec {
				workingDir = rootDir
				environment("ARCH", "x86_64")
				commandLine(
					appimagetool.absolutePath,
					"--appimage-extract-and-run",
					appDir.absolutePath,
					outputDir.resolve("hammer.AppImage").absolutePath
				)
			}.result.get()

			println("AppImage created: ${outputDir.resolve("hammer.AppImage")}")

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
				from(flatpakDir.resolve("com.darkrockstudios.hammer.desktop"))
				from(flatpakDir.resolve("com.darkrockstudios.hammer.metainfo.xml"))
				from(rootDir.resolve("desktop/icons/linux.png")) {
					rename { "com.darkrockstudios.hammer.png" }
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
			val manifestFile = flatpakDir.resolve("com.darkrockstudios.hammer.yaml")

			// Clean previous build artifacts
			buildDir.deleteRecursively()
			repoDir.deleteRecursively()
			outputDir.mkdirs()

			// Build the flatpak (install dependencies from flathub)
			providers.exec {
				workingDir = rootDir
				commandLine(
					"flatpak-builder",
					"--user",
					"--repo=${repoDir.absolutePath}",
					"--install-deps-from=flathub",
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
					"com.darkrockstudios.hammer"
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
 * Detects the current system architecture (x64 or aarch64).
 */
fun detectArchitecture(): String {
	val osArch = System.getProperty("os.arch").lowercase()
	return when {
		osArch.contains("aarch64") || osArch.contains("arm64") -> "aarch64"
		osArch.contains("amd64") || osArch.contains("x86_64") -> "x64"
		else -> osArch
	}
}

/**
 * Registers the createFlathubTarball task for creating a tar.gz suitable for Flathub.
 * This creates a self-contained distribution with the application, desktop file, and icon.
 * The architecture is automatically detected from the system.
 */
fun Project.registerCreateFlathubTarballTask(appVersion: String) {
	tasks.register("createFlathubTarball") {
		group = "distribution"
		description = "Creates a tar.gz distribution suitable for Flathub submission (auto-detects architecture)."

		dependsOn(":desktop:createReleaseDistributable")

		doLast {
			val arch = detectArchitecture()
			val appSourceDir = rootDir.resolve("desktop/build/installers/main-release/app/hammer")
			val outputDir = rootDir.resolve("desktop/build/installers/main-release/tarball")
			val stagingDir = outputDir.resolve("staging/hammer-$appVersion")
			val iconFile = rootDir.resolve("desktop/icons/linux.png")
			val desktopFile = rootDir.resolve("flatpak/com.darkrockstudios.hammer.desktop")

			// Clean and create directories
			outputDir.deleteRecursively()
			outputDir.mkdirs()
			stagingDir.mkdirs()

			// Copy the application
			copy {
				from(appSourceDir)
				into(stagingDir)
			}

			// Copy desktop file to root of staging directory
			copy {
				from(desktopFile)
				into(stagingDir)
				rename { "hammer-editor.desktop" }
			}

			// Copy icon to root of staging directory
			copy {
				from(iconFile)
				into(stagingDir)
				rename { "icon.png" }
			}

			// Create tar.gz with architecture in filename
			val tarballName = "hammer-$appVersion-linux-$arch.tar.gz"
			exec {
				workingDir = outputDir.resolve("staging")
				commandLine(
					"tar",
					"-czf",
					"../$tarballName",
					"hammer-$appVersion"
				)
			}

			println("Tarball created: ${outputDir.resolve(tarballName)}")
			println("Architecture: $arch")
			println("Size: ${outputDir.resolve(tarballName).length() / 1024 / 1024} MB")

			// Clean up staging directory
			stagingDir.parentFile.deleteRecursively()
		}
	}
}

/**
 * Registers all Linux distribution tasks (Snap, AppImage, Flatpak, Tarball).
 */
fun Project.registerLinuxDistributionTasks(appVersion: String) {
	registerBuildDistSnapTask(appVersion)
	registerBuildDistAppImageTask()
	registerBuildDistFlatpakTask()
	registerCreateFlathubTarballTask(appVersion)
}
