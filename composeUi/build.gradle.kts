import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.jetbrains.kover)
	//alias(libs.plugins.compose.report.generator)
	id("ee.schimke.composeai.preview") version "0.10.16"
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = libs.versions.app.get()

kotlin {
	androidLibrary {
		namespace = "com.darkrockstudios.apps.hammer.composeui"
		compileSdk = libs.versions.android.sdk.compile.get().toInt()
		minSdk = libs.versions.android.sdk.min.get().toInt()

		androidResources {
			enable = true
		}

		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	listOf(
		iosArm64(),
		iosSimulatorArm64()
	).forEach { iosTarget ->
		iosTarget.binaries.framework {
			baseName = "Hammer"
			// libbacktrace gives Kotlin/Native crashes file/line numbers in stack traces.
			binaryOption("sourceInfoType", "libbacktrace")
			export(project(":common"))
			export(libs.decompose)
			export(libs.essenty.lifecycle)
			export(libs.coroutines.core)
			export(libs.napier)
		}
	}

	applyDefaultHierarchyTemplate()

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
				optIn("com.arkivanov.decompose.ExperimentalDecomposeApi")
				optIn("androidx.compose.animation.ExperimentalSharedTransitionApi")
			}
		}

		val commonMain by getting {
			dependencies {
				api(project(":common"))
				api(libs.jetbrains.compose.runtime)
				api(libs.jetbrains.compose.components.resources)
				api(libs.jetbrains.compose.ui.tooling.preview)
				api(libs.jetbrains.compose.foundation)
				api(libs.jetbrains.compose.material)
				api(libs.jetbrains.compose.material3)
				api(libs.jetbrains.compose.animation)
				api(libs.jetbrains.compose.animation.graphics)
				api(libs.jetbrains.compose.material.icons.extended)
				api(libs.multiplatform.window.size)
				api(libs.jetbrains.compose.ui.util)
				api(libs.jetbrains.compose.ui.text)
				api(libs.jetbrains.compose.ui.backhandler)
				api(libs.decompose.compose)
				api(libs.decompose.compose.experimental)
				api(libs.koin.compose)
				api(libs.filekit.dialogs.compose)
				api(libs.coil.compose)
				api(libs.coil.svg)
				api(libs.kmpalette.core)
				api(libs.kmpalette.extensions.file)
				implementation(libs.colorpicker.compose)
				implementation(libs.material.kolor)
				implementation(libs.koalaplot.core)
				implementation(libs.aboutlibraries.core)
				implementation(libs.aboutlibraries.compose)
				implementation(libs.compose.texteditor)
				implementation(libs.compose.texteditor.find)
				implementation(libs.compose.texteditor.spellcheck)
				implementation(libs.platform.spellcheckerkt)
				implementation(libs.fluidsonic.locale)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.okio.fakefilesystem)
				implementation(libs.kotlin.reflect)
			}
		}
		val androidMain by getting {
			dependencies {
				api(libs.jetbrains.compose.ui.tooling)
				implementation(libs.androidx.window)
				implementation(libs.activity.compose)
				implementation(libs.moko.permissions.compose)
			}
		}
		val desktopMain by getting {
			dependencies {
				api(libs.jetbrains.compose.ui.tooling)
				implementation(compose.desktop.currentOs)
			}
		}
		val iosMain by getting

		val desktopTest by getting {
			dependencies {
				implementation(libs.junit.jupiter)
				runtimeOnly(libs.junit.vintage.engine)
				implementation(libs.mockk)
				implementation(libs.jetbrains.compose.ui.test.junit4)
			}
		}
	}
}

kover {
	reports {
		filters {
			includes {
				packages("com.darkrockstudios.apps.hammer.*")
			}
		}
	}
}