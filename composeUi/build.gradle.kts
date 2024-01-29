plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.android.library)
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = libs.versions.app.get()

kotlin {
	androidTarget()
	jvm("desktop") {
		compilations.all {
			kotlinOptions.jvmTarget = libs.versions.jvm.get()
		}
	}

	applyDefaultHierarchyTemplate()

	sourceSets {
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
			}
		}
		val desktopMain by getting {
			dependencies {
				dependsOn(commonMain) // TODO https://github.com/icerockdev/moko-resources/issues/557
				implementation(compose.desktop.currentOs)
				api(libs.jSystemThemeDetector)
			}
		}

		val desktopTest by getting {
			dependencies {
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
	buildFeatures {
		compose = true
	}
	composeOptions {
		kotlinCompilerExtensionVersion = libs.versions.jetpack.compose.compiler.get()
	}
	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
	}
}

koverReport {
	defaults {
		filters {
			includes {
				packages("com.darkrockstudios.apps.hammer.*")
			}
		}
	}
}