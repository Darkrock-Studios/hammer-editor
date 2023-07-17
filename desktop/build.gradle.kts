import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val app_version: String by extra
val data_version: String by extra
val jvm_version: String by extra
val moko_resources_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	kotlin("plugin.serialization")
	id("org.jetbrains.compose")
	id("org.jetbrains.kotlinx.kover")
	id("dev.icerock.mobile.multiplatform-resources")
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = app_version


kotlin {
	jvm {
		compilations.all {
			kotlinOptions.jvmTarget = jvm_version
		}
		withJava()
	}
	sourceSets {
		val commonMain by getting {
			resources.srcDirs("resources")
			dependencies {
				implementation("dev.icerock.moko:resources:$moko_resources_version")
			}
		}
		val jvmMain by getting {
			dependencies {
				implementation(project(":base"))
				implementation(project(":common"))
				implementation(project(":composeUi"))
				implementation(compose.preview)
				implementation(compose.desktop.currentOs)
				implementation("com.github.weisj:darklaf-core:3.0.2")
				implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
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
			packageVersion = app_version
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