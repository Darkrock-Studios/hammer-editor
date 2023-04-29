val app_version: String by extra
//val androidx_compose_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val jetbrains_compose_version: String by extra
val jetpack_compose_compiler_version: String by extra
val koin_version: String by extra
val android_version_code: String by extra

val RELEASE_STORE_FILE = System.getenv("RELEASE_STORE_FILE") ?: "/"
val RELEASE_STORE_PASSWORD = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
val RELEASE_KEY_ALIAS = System.getenv("RELEASE_KEY_ALIAS") ?: ""
val RELEASE_KEY_PASSWORD = System.getenv("RELEASE_KEY_PASSWORD") ?: ""

plugins {
	kotlin("android")
	kotlin("plugin.serialization")
	id("com.android.application")
	id("org.jetbrains.compose")
	id("org.jetbrains.kotlinx.kover")
}

group = "com.darkrockstudios.apps.hammer"
version = app_version

repositories {
	mavenCentral()
}

dependencies {
	api(project(":composeUi"))
	implementation("androidx.activity:activity-compose:1.7.1")
	implementation("io.insert-koin:koin-android:$koin_version")
	implementation("androidx.glance:glance-appwidget:1.0.0-alpha05")
	implementation("androidx.work:work-runtime-ktx:2.8.1")
	implementation("com.google.android.material:material:1.8.0")
	implementation("androidx.appcompat:appcompat:1.6.1")
}

android {
	namespace = "com.darkrockstudios.apps.hammer.android"
	compileSdk = android_compile_sdk.toInt()
	defaultConfig {
		applicationId = "com.darkrockstudios.apps.hammer.android"
		minSdk = android_min_sdk.toInt()
		targetSdk = android_target_sdk.toInt()
		versionCode = android_version_code.toInt()
		versionName = app_version
	}
	buildFeatures {
		compose = true
	}
	composeOptions {
		kotlinCompilerExtensionVersion = jetpack_compose_compiler_version
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	signingConfigs {
		create("release") {
			keyAlias = RELEASE_KEY_ALIAS
			keyPassword = RELEASE_KEY_PASSWORD
			storeFile = file(RELEASE_STORE_FILE)
			storePassword = RELEASE_STORE_PASSWORD
		}
	}

	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
			isShrinkResources = false

			signingConfig = signingConfigs.getByName("release")

			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				File("proguard-rules.pro")
			)
		}
	}
}