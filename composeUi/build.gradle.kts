import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.android.library)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.moko.resources)
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = libs.versions.app.get()

kotlin {
	androidTarget()
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	applyDefaultHierarchyTemplate()

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
			}
		}

		val commonMain by getting {
			@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
			dependencies {
				api(project(":common"))
				api(compose.runtime)
				api(compose.uiTooling)
				api(compose.preview)
				api(compose.foundation)
				api(compose.material3)
				api(compose.animation)
				api(compose.animationGraphics)
				api(compose.materialIconsExtended)
				api(libs.multiplatform.window.size)
				api(libs.jetbrains.compose.ui.util)
				api(libs.jetbrains.compose.ui.text)
				api(libs.decompose.compose)
				api(libs.richtexteditor)
				api(libs.mpfilepicker)
				api(libs.imageloader)
				api(libs.imageloader.moko)
				api(libs.imageloader.blur)
				implementation(libs.koalaplot.core)
				api(libs.moko.resources.compose)
				implementation(libs.aboutlibraries.core)
				implementation(libs.aboutlibraries.compose)
				implementation(libs.composericheditor)
			}
		}
		val commonTest by getting {
			@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.okio.fakefilesystem)
				implementation(libs.kotlin.reflect)
				api(compose.uiTestJUnit4)
			}
		}
		val androidMain by getting {
			dependencies {
				api(libs.koin.compose)
				implementation(libs.androidx.window)
				implementation(libs.moko.permissions.compose)
			}
		}
		val desktopMain by getting {
			dependencies {
				implementation(compose.desktop.currentOs)
				api(libs.jSystemThemeDetector)
			}
		}

		val desktopTest by getting {
			dependencies {
				implementation(libs.androidx.junit)
				implementation(libs.junit.jupiter)
				runtimeOnly(libs.junit.vintage.engine)
				implementation(libs.mockk)
			}
		}
	}
}

android {
	namespace = "com.darkrockstudios.apps.hammer.composeui"
	compileSdk = libs.versions.android.sdk.compile.get().toInt()
	sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
	sourceSets["main"].res.srcDirs("src/androidMain/res", "src/commonMain/resources")
	defaultConfig {
		minSdk = libs.versions.android.sdk.min.get().toInt()
		lint.targetSdk = libs.versions.android.sdk.target.get().toInt()
	}
	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
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