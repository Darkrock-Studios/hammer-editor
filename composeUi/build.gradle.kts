val jetbrains_compose_version: String by extra
val jetpack_compose_compiler_version: String by extra
val mockk_version: String by extra
val moko_resources_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.android.library)
	id("kotlin-parcelize")
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = libs.versions.app.get()

kotlin {
	android()
	jvm("desktop") {
		compilations.all {
			kotlinOptions.jvmTarget = libs.versions.jvm.get()
		}
	}

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
				api("dev.chrisbanes.material3:material3-window-size-class-multiplatform:0.2.0")
				api("org.jetbrains.compose.ui:ui-util:$jetbrains_compose_version")
				api("org.jetbrains.compose.ui:ui-text:$jetbrains_compose_version")
				api(libs.decompose.compose)
				api("com.darkrockstudios:richtexteditor:1.4.1")
				api("com.darkrockstudios:mpfilepicker:1.2.0")
				api("io.github.qdsfdhvh:image-loader:1.5.1")
				implementation("io.github.koalaplot:koalaplot-core:0.3.0")
				api("dev.icerock.moko:resources-compose:$moko_resources_version")
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
			}
		}
		val desktopMain by getting {
			dependencies {
				implementation(compose.desktop.currentOs)
				api("com.github.Dansoftowner:jSystemThemeDetector:3.8")
			}
		}

		val desktopTest by getting {
			dependencies {
				implementation("io.mockk:mockk:$mockk_version")
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
		targetSdk = libs.versions.android.sdk.target.get().toInt()
	}
	buildFeatures {
		compose = true
	}
	composeOptions {
		kotlinCompilerExtensionVersion = jetpack_compose_compiler_version
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kover {
	filters {
		classes {
			includes += "com.darkrockstudios.apps.hammer.*"
		}
	}
}