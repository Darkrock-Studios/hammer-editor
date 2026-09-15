import com.darkrockstudios.build.getWearVersionCode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val RELEASE_STORE_FILE = System.getenv("RELEASE_STORE_FILE") ?: "/"
val RELEASE_STORE_PASSWORD = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
val RELEASE_KEY_ALIAS = System.getenv("RELEASE_KEY_ALIAS") ?: ""
val RELEASE_KEY_PASSWORD = System.getenv("RELEASE_KEY_PASSWORD") ?: ""

// Wear OS 3 is the first release that runs standalone apps like this one.
val WEAR_MIN_SDK = 30

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

dependencies {
	implementation(project(":common"))
	implementation(libs.activity.compose)
	implementation(libs.koin.android)
	implementation(libs.decompose.compose)
	implementation(libs.decompose.android)
	implementation(libs.work.runtime.ktx)
	implementation(libs.androidx.datastore)
	implementation(libs.wear.compose.material3)
	implementation(libs.wear.compose.foundation)
	implementation(libs.wear.compose.ui.tooling)
	implementation(libs.wear.input)
	implementation(libs.play.services.wearable)
	implementation(libs.coroutines.play.services)
	implementation(libs.lifecycle.process)
	debugImplementation(libs.jetbrains.compose.ui.tooling)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.bundles.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.koin.test)
	testImplementation(libs.mockk)
	testImplementation(libs.jetbrains.compose.components.resources)
	testImplementation(libs.okio.fakefilesystem)
}

android {
	namespace = "com.darkrockstudios.apps.hammer.wear"
	compileSdk = libs.versions.android.sdk.compile.get().toInt()
	defaultConfig {
		// Shares the phone's id so Play lists both under one app and the Data Layer pairs them.
		applicationId = "com.darkrockstudios.apps.hammer.android"
		minSdk = WEAR_MIN_SDK
		targetSdk = libs.versions.android.sdk.target.get().toInt()
		versionCode = getWearVersionCode(libs.versions.app.get())
		versionName = libs.versions.app.get()
	}
	buildFeatures {
		compose = true
		buildConfig = true
	}
	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
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
		debug {
			applicationIdSuffix = ".dev"
			versionNameSuffix = "-dev"
		}
		release {
			isMinifyEnabled = true
			isShrinkResources = true

			signingConfig = signingConfigs.getByName("release")

			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				rootProject.file("proguard-common.pro"),
				file("proguard-rules.pro"),
			)
		}
	}
	testOptions {
		unitTests.isReturnDefaultValues = true
	}
	packaging {
		resources {
			excludes += setOf(
				"/META-INF/{AL2.0,LGPL2.1}",
				"/META-INF/versions/9/previous-compilation-data.bin",
				"/META-INF/LICENSE.md",
				"/META-INF/LICENSE-notice.md"
			)
		}
	}
}

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
	compilerOptions {
		jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
	}
}
