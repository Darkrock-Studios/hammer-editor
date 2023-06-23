val app_version: String by extra
val jvm_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val kotlin_version: String by extra
val jetbrains_compose_version: String by extra
val jetpack_compose_compiler_version: String by extra
val decompose_version: String by extra
val mockk_version: String by extra
val okio_version: String by extra
val koin_version: String by extra
val moko_resources_version: String by extra

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
	id("org.jetbrains.compose")
	id("com.android.library")
	id("kotlin-parcelize")
	id("org.jetbrains.kotlinx.kover")
}

group = "com.darkrockstudios.apps.hammer.composeui"
version = app_version

kotlin {
	android()
	jvm("desktop") {
		compilations.all {
			kotlinOptions.jvmTarget = jvm_version
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
				api("org.jetbrains.compose.ui:ui-util:$jetbrains_compose_version")
				api("org.jetbrains.compose.ui:ui-text:$jetbrains_compose_version")
				api("com.arkivanov.decompose:extensions-compose-jetbrains:$decompose_version")
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
				implementation("com.squareup.okio:okio-fakefilesystem:$okio_version")
				implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
				api(compose.uiTestJUnit4)
			}
		}
		val androidMain by getting {
			dependencies {
				api("io.insert-koin:koin-androidx-compose:3.4.1")
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
	compileSdk = android_compile_sdk.toInt()
	sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
	sourceSets["main"].res.srcDirs("src/androidMain/res", "src/commonMain/resources")
	defaultConfig {
		minSdk = android_min_sdk.toInt()
		targetSdk = android_target_sdk.toInt()
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