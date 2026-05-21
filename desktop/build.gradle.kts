import com.darkrockstudios.build.registerLinuxDistributionTasks
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile

val data_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.aboutlibraries.plugin)
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = libs.versions.app.get()

// -PmacOsAppStoreRelease=true -PbuildNumber=N enables App Store packaging.
val isAppStoreRelease: Boolean =
	(project.findProperty("macOsAppStoreRelease") as String?)?.toBoolean() ?: false
val macBuildNumber: String =
	(project.findProperty("buildNumber") as String?) ?: "1"

// Keep JNA classes and the pre-bundled libjnidispatch.jnilib at one version.
configurations.all {
	resolutionStrategy {
		force("net.java.dev.jna:jna:5.18.1")
		force("net.java.dev.jna:jna-platform:5.18.1")
	}
}

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
				implementation(libs.jetbrains.compose.components.ui.tooling.preview)
				implementation(compose.desktop.currentOs)
				implementation(libs.kotlinx.cli)
				implementation(libs.nucleus.darkmode.detector)
				implementation(libs.nucleus.decorated.window.jbr)
				implementation(libs.nucleus.decorated.window.material3)
				implementation(libs.nucleus.launcher.windows)
				implementation(libs.nucleus.launcher.linux)
				implementation(libs.nucleus.launcher.macos)
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

			appResourcesRootDir.set(project.layout.buildDirectory.dir("macos-native-libs"))

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
				bundleID = "com.darkrockstudios.apps.hammer"
				dockName = "Hammer"
				appCategory = "public.app-category.productivity"
				minimumSystemVersion = "12.0"
				appStore = isAppStoreRelease

				iconFile.set(project.file("icons/macos.icns"))

				infoPlist {
					packageBuildVersion = macBuildNumber
					extraKeysRawXml = """
						<key>ITSAppUsesNonExemptEncryption</key>
						<false/>
					""".trimIndent()
				}

				if (isAppStoreRelease) {
					signing {
						sign.set(true)
						// Team Name from `security find-identity -v -p codesigning`.
						identity.set("Adam Brown")
					}
					provisioningProfile.set(project.file("embedded.provisionprofile"))
					runtimeProvisioningProfile.set(project.file("runtime.provisionprofile"))
					entitlementsFile.set(project.file("entitlements.plist"))
					runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
				}
			}
		}
		jvmArgs("-Dcompose.application.configure.swing.globals=false")
		// Load libjnidispatch.jnilib from Contents/app/resources/, never extract.
		jvmArgs("-Djna.nounpack=true", "-Djna.nosys=true")
		if (isAppStoreRelease) {
			jvmArgs("-Dhammer.app.store=true")
			// Lets Nucleus' System.loadLibrary find our pre-bundled signed dylibs
			// instead of falling back to extraction (Gatekeeper blocks that).
			jvmArgs("-Djava.library.path=\$APPDIR/resources")
		}

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

// Pulled from the resolved jars so versions stay in sync with the deps.
val extractMacosNativeLibs = tasks.register("extractMacosNativeLibs") {
	group = "macOS"
	description = "Extract macOS arm64 native libs from JNA + Nucleus jars into resources/macos/"
	onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

	val outDir = layout.buildDirectory.dir("macos-native-libs/macos").get().asFile
	outputs.dir(outDir)

	val nativeLibs = listOf(
		Triple("net.java.dev.jna", "jna", "com/sun/jna/darwin-aarch64/libjnidispatch.jnilib"),
		Triple("io.github.kdroidfilter", "nucleus.launcher-macos", "nucleus/native/darwin-aarch64/libnucleus_launcher_macos.dylib"),
		Triple("io.github.kdroidfilter", "nucleus.darkmode-detector", "nucleus/native/darwin-aarch64/libnucleus_darkmode.dylib"),
		Triple("io.github.kdroidfilter", "nucleus.decorated-window-core", "nucleus/native/darwin-aarch64/libnucleus_layout_direction.dylib"),
		Triple("io.github.kdroidfilter", "nucleus.decorated-window-jbr", "nucleus/native/darwin-aarch64/libnucleus_macos.dylib"),
	)

	val runtime = configurations.named("jvmRuntimeClasspath")
	inputs.files(runtime).withPropertyName("jvmRuntimeClasspath")
		.withPathSensitivity(PathSensitivity.RELATIVE)

	doLast {
		outDir.mkdirs()
		val artifacts = runtime.get().resolvedConfiguration.resolvedArtifacts
		nativeLibs.forEach { (group, artifact, entry) ->
			val jar = artifacts.firstOrNull {
				it.moduleVersion.id.group == group && it.moduleVersion.id.name == artifact
			}?.file ?: error("$group:$artifact not found on jvmRuntimeClasspath")
			val outFile = outDir.resolve(entry.substringAfterLast('/'))
			ZipFile(jar).use { zip ->
				val ze = zip.getEntry(entry) ?: error("$entry not found in ${jar.name}")
				outFile.outputStream().use { out -> zip.getInputStream(ze).copyTo(out) }
			}
			logger.lifecycle("Extracted ${outFile.name} from ${jar.name}")
		}
	}
}
tasks.matching { it.name == "prepareAppResources" }.configureEach {
	dependsOn(extractMacosNativeLibs)
}

// Any quarantine xattr on bundled files trips App Store validation.
val unquarantineMacApp = tasks.register("unquarantineMacApp") {
	group = "macOS"
	description = "Remove com.apple.quarantine xattr from the built .app before signing"
	onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
	dependsOn("createReleaseDistributable")
	val appDir = layout.buildDirectory.dir("installers/main-release/app")
	val execOps = project.providers
	doLast {
		val dir = appDir.get().asFile
		val app = dir.listFiles { f -> f.isDirectory && f.name.endsWith(".app") }?.firstOrNull()
			?: error("No .app bundle found in $dir after createReleaseDistributable")
		execOps.exec {
			executable = "xattr"
			args("-dr", "com.apple.quarantine", app.absolutePath)
		}.result.get()
	}
}
// Compose Desktop doesn't sign files under Contents/app/resources/ — they
// end up adhoc-signed and fail App Store validation (ITMS-90238).
val signMacAppResources = tasks.register("signMacAppResources") {
	group = "macOS"
	description = "Re-sign Mach-O files under Contents/app/resources/ and re-seal the bundle"
	onlyIf { isAppStoreRelease && org.gradle.internal.os.OperatingSystem.current().isMacOsX }
	dependsOn(unquarantineMacApp)
	val appDir = layout.buildDirectory.dir("installers/main-release/app")
	val appEntitlements = project.file("entitlements.plist")
	val runtimeEntitlements = project.file("runtime-entitlements.plist")
	val signingIdentity = "3rd Party Mac Developer Application: Adam Brown (8P3G3HT4J5)"
	val execOps = project.providers
	doLast {
		val dir = appDir.get().asFile
		val app = dir.listFiles { f -> f.isDirectory && f.name.endsWith(".app") }?.firstOrNull()
			?: error("No .app bundle found in $dir")
		val resourcesDir = app.resolve("Contents/app/resources")
		if (resourcesDir.isDirectory) {
			resourcesDir.walkTopDown().filter {
				it.isFile && (it.name.endsWith(".dylib") ||
					it.name.endsWith(".jnilib") ||
					it.name.endsWith(".so"))
			}.forEach { f ->
				logger.lifecycle("Re-signing ${f.relativeTo(app)}")
				execOps.exec {
					executable = "codesign"
					args(
						"--force",
						"--options", "runtime",
						"--timestamp",
						"--entitlements", runtimeEntitlements.absolutePath,
						"--sign", signingIdentity,
						f.absolutePath
					)
				}.result.get()
			}
		}
		// Re-seal the outermost bundle (no --deep) to pick up the new hashes.
		logger.lifecycle("Re-sealing ${app.name}")
		execOps.exec {
			executable = "codesign"
			args(
				"--force",
				"--options", "runtime",
				"--timestamp",
				"--entitlements", appEntitlements.absolutePath,
				"--sign", signingIdentity,
				app.absolutePath
			)
		}.result.get()
	}
}
tasks.matching { it.name == "packageReleasePkg" }.configureEach {
	dependsOn(signMacAppResources)
}

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
	val searchPaths = listOf(
		"C:\\Program Files (x86)\\Windows Kits\\10\\bin",
		"C:\\Program Files (x86)\\Windows Kits\\10\\App Certification Kit"
	)

	// Prefer x64 over x86/arm/arm64 — walk() ordering is filesystem-dependent
	// and on some runners the arm64 copy is encountered first.
	val archPriority = listOf("x64", "x86", "arm", "arm64")

	val candidates = mutableListOf<java.io.File>()
	for (basePath in searchPaths) {
		val baseDir = file(basePath)
		if (baseDir.exists()) {
			baseDir.walk().forEach { f ->
				if (f.name == "makeappx.exe") {
					candidates += f
				}
			}
		}
	}

	return candidates
		.sortedBy { f ->
			val arch = f.parentFile?.name?.lowercase() ?: ""
			archPriority.indexOf(arch).let { if (it == -1) Int.MAX_VALUE else it }
		}
		.firstOrNull()
		?.absolutePath
}

