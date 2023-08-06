import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val data_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.moko.resources)
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = libs.versions.app.get()


kotlin {
	jvm {
		compilations.all {
			kotlinOptions.jvmTarget = libs.versions.jvm.get()
		}
		withJava()
	}
	sourceSets {
		val commonMain by getting {
			resources.srcDirs("resources")
			dependencies {
				implementation(libs.moko.resources)
			}
		}
		val jvmMain by getting {
			dependencies {
				implementation(project(":base"))
				implementation(project(":common"))
				implementation(project(":composeUi"))
				implementation(compose.preview)
				implementation(compose.desktop.currentOs)
				implementation(libs.darklaf.core)
				implementation(libs.kotlinx.cli)
			}
		}
		val jvmTest by getting
	}
}

compose.desktop {
	application {
		mainClass = "com.darkrockstudios.apps.hammer.desktop.MainKt"
		nativeDistributions {
			targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
			includeAllModules = true
			packageName = "hammer"
			packageVersion = libs.versions.app.get()
			description = "A simple tool for building stories."
			copyright = "© 2023 Adam W. Brown, All rights reserved."
			licenseFile.set(project.file("../LICENSE"))
			outputBaseDir.set(project.buildDir.resolve("installers"))

			windows.apply {
				menuGroup = "Hammer"
				shortcut = true
				console = true
			}

			linux.apply {
				rpmLicenseType = "MIT"
			}

			macOS.apply {
				appStore = false
			}
		}
		jvmArgs("-Dcompose.application.configure.swing.globals=false")

		buildTypes.release.proguard {
			isEnabled.set(false)
			configurationFiles.from("proguard-rules.pro")
		}
	}
}

multiplatformResources {
	multiplatformResourcesClassName = "DR"
	multiplatformResourcesPackage = "com.darkrockstudios.apps.hammer.desktop"
	multiplatformResourcesSourceSet = "commonMain"
}