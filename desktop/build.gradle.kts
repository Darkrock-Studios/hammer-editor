import com.darkrockstudios.build.registerLinuxDistributionTasks
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val data_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.aboutlibraries.plugin)
	alias(libs.plugins.flatpak.gradle.generator)
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = libs.versions.app.get()


kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}
	jvm()
	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
			}
		}

		val commonMain by getting {
			resources.srcDirs("resources")
			dependencies {
				implementation(libs.aboutlibraries.core)
				implementation(libs.multiplatform.settings)
			}
		}
		val jvmMain by getting {
			dependencies {
				implementation(project(":base"))
				implementation(project(":common"))
				implementation(project(":composeUi"))
				implementation(compose.components.uiToolingPreview)
				implementation(compose.desktop.currentOs)
				implementation(libs.kotlinx.cli)
				implementation(libs.nucleus.darkmode.detector)
				implementation(libs.nucleus.decorated.window.jbr)
			}
		}
		val jvmTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.bundles.junit.jupiter)
			}
		}
	}
}

// Pin jpackage's bundled runtime to the JetBrains Runtime.
val jbrLauncher = javaToolchains.launcherFor {
	languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
	vendor.set(JvmVendorSpec.JETBRAINS)
}

compose.desktop {
	application {
		javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
		mainClass = "com.darkrockstudios.apps.hammer.desktop.MainKt"
		nativeDistributions {
			targetFormats(
				TargetFormat.Dmg,
				TargetFormat.Pkg,
				TargetFormat.AppImage,
				TargetFormat.Msi,
				TargetFormat.Exe,
				TargetFormat.Deb,
				TargetFormat.Rpm
			)
			modules = arrayListOf(":base", ":common", ":composeUi", ":desktop")
			includeAllModules = true
			packageName = "hammer"
			packageVersion = libs.versions.app.get()
			description = "A simple tool for building stories."
			copyright = "© 2025 Adam W. Brown, All rights reserved."
			licenseFile.set(project.file("../LICENSE"))
			outputBaseDir.set(project.layout.buildDirectory.dir("installers"))

			windows {
				menuGroup = "Hammer"
				shortcut = true
				console = false

				iconFile.set(project.file("icons/windows.ico"))
			}

			linux {
				rpmLicenseType = "MIT"
				shortcut = true

				iconFile.set(project.file("icons/linux.png"))
			}

			macOS {
				dockName = "Hammer"
				appStore = false

				iconFile.set(project.file("icons/macos.icns"))
			}
		}
		jvmArgs("-Dcompose.application.configure.swing.globals=false")

		buildTypes.release.proguard {
			version.set("7.6.0")
			isEnabled.set(true)
			obfuscate.set(false)
			optimize.set(false)
			//joinOutputJars.set(true)
			configurationFiles.from("proguard-rules.pro")
		}
	}
}

aboutLibraries {
	export {
		prettyPrint = true
		excludeFields.addAll("generated")
	}
}

registerLinuxDistributionTasks(libs.versions.app.get())

// MSIX packaging task for Windows Store
tasks.register("packageMsix") {
	group = "distribution"
	description = "Creates an MSIX package for Microsoft Store submission"

	// Only run on Windows
	onlyIf {
		org.gradle.internal.os.OperatingSystem.current().isWindows
	}

	// Depend on the distributable being created first
	dependsOn("createDistributable")

	doLast {
		val appVersion = libs.versions.app.get()
		val msixVersion = "$appVersion.0" // MSIX requires 4-part version

		val buildDir = project.layout.buildDirectory.get().asFile
		val distributableDir = buildDir.resolve("installers/main/app/hammer")
		val msixDir = project.rootDir.resolve("msix")
		val outputMsix = buildDir.resolve("installers/Hammer-${appVersion}.msix")

		// Copy manifest
		println("Copying AppxManifest.xml...")
		val manifestSrc = msixDir.resolve("AppxManifest.xml")
		val manifestDst = distributableDir.resolve("AppxManifest.xml")
		manifestSrc.copyTo(manifestDst, overwrite = true)

		// Update version in manifest (only in Identity element)
		println("Updating version to $msixVersion...")
		val manifestContent = manifestDst.readText()
		val updatedManifest = manifestContent.replace(
			Regex("""(<Identity[^>]*Version=")[\d\.]+""""),
			"$1$msixVersion\""
		)
		manifestDst.writeText(updatedManifest)

		// Copy assets
		println("Copying Assets...")
		val assetsSrc = msixDir.resolve("Assets")
		val assetsDst = distributableDir.resolve("Assets")
		assetsSrc.copyRecursively(assetsDst, overwrite = true)

		// Find makeappx.exe
		val makeappxPath = findMakeAppx()
		if (makeappxPath == null) {
			throw GradleException(
				"makeappx.exe not found. Please install Windows SDK.\n" +
				"Download from: https://developer.microsoft.com/en-us/windows/downloads/windows-sdk/"
			)
		}

		println("Packaging MSIX with makeappx...")
		val result = project.providers.exec {
			commandLine(
				makeappxPath,
				"pack",
				"/d", distributableDir.absolutePath,
				"/p", outputMsix.absolutePath,
				"/o"
			)
			isIgnoreExitValue = true
		}.result.get()

		if (result.exitValue == 0) {
			println("✓ MSIX package created successfully!")
			println("  Location: ${outputMsix.absolutePath}")
			println("  Size: ${String.format("%.2f MB", outputMsix.length() / 1024.0 / 1024.0)}")
			println("\nReady for Microsoft Store submission!")
		} else {
			throw GradleException("Failed to create MSIX package")
		}
	}
}

fun findMakeAppx(): String? {
	// Common Windows SDK locations
	val searchPaths = listOf(
		"C:\\Program Files (x86)\\Windows Kits\\10\\bin",
		"C:\\Program Files (x86)\\Windows Kits\\10\\App Certification Kit"
	)

	for (basePath in searchPaths) {
		val baseDir = file(basePath)
		if (baseDir.exists()) {
			// Search in bin subdirectories (e.g., 10.0.22621.0/x64/)
			baseDir.walk().forEach { file ->
				if (file.name == "makeappx.exe") {
					return file.absolutePath
				}
			}
		}
	}

	return null
}

tasks.named("flatpakGradleGenerator") {
	setProperty("outputFile", rootProject.file("flatpak/flatpak-sources.json"))

	// Exclude test configurations to reduce the dependency list
	setProperty("excludeConfigurations", listOf(
		"testCompileClasspath",
		"testRuntimeClasspath",
		"jvmTestCompileClasspath",
		"jvmTestRuntimeClasspath"
	))

	// Set architecture (x86_64 is the default, but can specify for clarity)
	// setProperty("onlyArches", "x86_64")
}
