import io.github.kdroidfilter.nucleus.desktop.application.dsl.AppImageCategory
import io.github.kdroidfilter.nucleus.desktop.application.dsl.SnapCompression
import io.github.kdroidfilter.nucleus.desktop.application.dsl.SnapConfinement
import io.github.kdroidfilter.nucleus.desktop.application.dsl.SnapGrade
import io.github.kdroidfilter.nucleus.desktop.application.dsl.SnapPlug
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat

val data_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
	// Nucleus brings its own forked compose-desktop application tasks;
	// applying org.jetbrains.compose alongside it collides on task names.
	alias(libs.plugins.nucleus)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.aboutlibraries.plugin)
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = libs.versions.app.get()


kotlin {
	// JDK 25 toolchain required for Nucleus AOT cache (Project Leyden).
	jvmToolchain(libs.versions.desktopJvm.get().toInt())
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
				// Explicit coords — org.jetbrains.compose plugin isn't applied here
				// because nucleus brings its own forked desktop-application tasks.
				implementation(libs.jetbrains.compose.ui.tooling.preview)
				implementation(libs.jetbrains.compose.desktop)
				implementation(libs.nucleus.aot.runtime)
				implementation(libs.darklaf.core)
				implementation(libs.kotlinx.cli)
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

nucleus.application {
	mainClass = "com.darkrockstudios.apps.hammer.desktop.MainKt"
	jvmArgs += listOf("-Dcompose.application.configure.swing.globals=false")

	buildTypes.release.proguard {
		version.set("7.6.0")
		isEnabled.set(true)
		obfuscate.set(false)
		optimize.set(false)
		configurationFiles.from("proguard-rules.pro")
	}

	nativeDistributions {
		targetFormats(
			// macOS
			TargetFormat.Dmg,
			TargetFormat.Pkg,
			// Windows
			TargetFormat.Msi,
			TargetFormat.Exe,        // alias for Nsis
			TargetFormat.Portable,
			TargetFormat.AppX,       // replaces bespoke packageMsix
			// Linux
			TargetFormat.Deb,
			TargetFormat.Rpm,
			TargetFormat.AppImage,
			TargetFormat.Snap,       // replaces bespoke snapcraft build
			TargetFormat.Flatpak,    // replaces bespoke flatpak-builder
		)
		includeAllModules = true
		enableAotCache = true
		packageName = "hammer"
		packageVersion = libs.versions.app.get()
		description = "A simple tool for building stories."
		copyright = "© 2025 Adam W. Brown, All rights reserved."
		vendor = "Dark Rock Studios"
		homepage = "https://github.com/Wavesonics/hammer-editor"
		licenseFile.set(project.file("../LICENSE"))

		windows {
			menuGroup = "Hammer"
			console = false
			iconFile.set(project.file("icons/windows.ico"))

			appx {
				// Mirrors the previous msix/AppxManifest.xml.
				applicationId = "Hammer"
				identityName = "DarkRockStudios.HammerEditor"
				publisher = "CN=1CB419E3-12E3-4F8B-B2CC-2C4F16D8E686"
				displayName = "Hammer Editor"
				publisherDisplayName = "Dark Rock Studios"
				languages = listOf(
					"en-US", "de-DE", "fr-FR", "es-ES", "it-IT", "pt-BR", "uk-UA"
				)
				storeLogo.set(project.file("../msix/Assets/StoreLogo.png"))
				square44x44Logo.set(project.file("../msix/Assets/Square44x44Logo.png"))
				square150x150Logo.set(project.file("../msix/Assets/Square150x150Logo.png"))
				wide310x150Logo.set(project.file("../msix/Assets/Wide310x150Logo.png"))
			}
		}

		linux {
			shortcut = true
			rpmLicenseType = "MIT"
			debMaintainer = "Adam W. Brown <adamwbrown@gmail.com>"
			iconFile.set(project.file("icons/linux.png"))

			appImage {
				category = AppImageCategory.Office
				genericName = "Story Editor"
				synopsis = "A simple tool for building stories."
				desktopEntries = mapOf("Keywords" to "writing;editor;story;novel;")
			}

			snap {
				confinement = SnapConfinement.Strict
				grade = SnapGrade.Stable
				summary = "A simple tool for building stories"
				base = "core22"
				compression = SnapCompression.Xz
				plugs = listOf(
					SnapPlug.Home,
					SnapPlug.Network,
					SnapPlug.NetworkBind,
					SnapPlug.Desktop,
					SnapPlug.DesktopLegacy,
					SnapPlug.Wayland,
					SnapPlug.X11,
					SnapPlug.Opengl,
					SnapPlug.RemovableMedia,
				)
			}

			flatpak {
				runtime = "org.freedesktop.Platform"
				runtimeVersion = "24.08"
				sdk = "org.freedesktop.Sdk"
				finishArgs = listOf(
					"--socket=x11",
					"--socket=wayland",
					"--share=ipc",
					"--share=network",
					"--device=dri",
					"--filesystem=home",
					"--filesystem=xdg-documents",
				)
				license.set(project.file("../LICENSE"))
			}
		}

		macOS {
			dockName = "Hammer"
			bundleID = "studio.darkrock.hammer"
			iconFile.set(project.file("icons/macos.icns"))
		}
	}
}

aboutLibraries {
	export {
		prettyPrint = true
		excludeFields.addAll("generated")
	}
}
