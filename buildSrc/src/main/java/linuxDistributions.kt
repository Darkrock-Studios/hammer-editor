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
			exec {
				workingDir = rootDir
				commandLine("snapcraft")
			}

			val snapFile = rootDir.resolve("hammer-editor_${appVersion}_amd64.snap")
			val outputDir = rootDir.resolve("desktop/build/installers/main/snap")
			outputDir.mkdirs()
			if (snapFile.exists()) {
				snapFile.copyTo(outputDir.resolve("hammer.snap"), overwrite = true)
				println("Snap package created: ${outputDir.resolve("hammer.snap")}")
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
				exec {
					workingDir = rootDir
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
				workingDir = rootDir
				environment("ARCH", "x86_64")
				commandLine(
					appimagetool.absolutePath,
					"--appimage-extract-and-run",
					appDir.absolutePath,
					outputDir.resolve("hammer.AppImage").absolutePath
				)
			}

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

		doFirst {
			requireLinux("buildDistFlatpak")
		}

		doLast {
			val buildDir = rootDir.resolve("flatpak-build")
			val repoDir = rootDir.resolve("flatpak-repo")
			val outputDir = rootDir.resolve("desktop/build/installers/main/flatpak")
			val manifestFile = rootDir.resolve("flatpak/com.darkrockstudios.hammer.yaml")

			// Clean previous build artifacts
			buildDir.deleteRecursively()
			repoDir.deleteRecursively()
			outputDir.mkdirs()

			// Build the flatpak (install dependencies from flathub)
			exec {
				workingDir = rootDir
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
				workingDir = rootDir
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
				workingDir = rootDir
				commandLine(
					"flatpak",
					"build-bundle",
					repoDir.absolutePath,
					outputDir.resolve("hammer.flatpak").absolutePath,
					"com.darkrockstudios.hammer"
				)
			}

			println("Flatpak created: ${outputDir.resolve("hammer.flatpak")}")

			// Clean up build directories
			buildDir.deleteRecursively()
			repoDir.deleteRecursively()
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
