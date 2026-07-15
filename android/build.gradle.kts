import com.darkrockstudios.build.getVersionCode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Mirrors the F-Droid detection in settings.gradle.kts. F-Droid builds may declare
// storage permissions (for public-storage projects) that Google Play disallows, so the
// extra permissions live in src/fdroid/AndroidManifest.xml and are only used here.
val isFDroidBuild = project.findProperty("fdroid")?.toString()?.isNotEmpty() == true ||
	System.getenv("FDROID_BUILD") != null

val RELEASE_STORE_FILE = System.getenv("RELEASE_STORE_FILE") ?: "/"
val RELEASE_STORE_PASSWORD = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
val RELEASE_KEY_ALIAS = System.getenv("RELEASE_KEY_ALIAS") ?: ""
val RELEASE_KEY_PASSWORD = System.getenv("RELEASE_KEY_PASSWORD") ?: ""

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.aboutlibraries.plugin.android)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

repositories {
	mavenCentral()
}

dependencies {
	api(project(":composeUi"))
	implementation(libs.activity.compose)
	implementation(libs.koin.android)
	implementation(libs.glance)
	implementation(libs.glance.appwidget)
	implementation(libs.glance.material3)
	implementation(libs.androidx.datastore)
	implementation(libs.tomlkt)
	implementation(libs.work.runtime.ktx)
	implementation(libs.material)
	implementation(libs.appcompat)
	implementation(libs.multiplatform.settings)

	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.junit.ktx)
	androidTestImplementation(libs.core)
	androidTestImplementation(libs.core.ktx)
	androidTestImplementation(libs.androidx.runner)
	androidTestImplementation(libs.jetbrains.compose.ui.test.junit4)
	// Compose ui-test drags in espresso-core 3.5.0, whose InputManagerEventInjectionStrategy
	// reflectively calls the removed InputManager.getInstance() and crashes on API 35+.
	androidTestImplementation(libs.espresso.core)
	androidTestUtil(libs.orchestrator)

	implementation(libs.aboutlibraries.core)
	testImplementation(libs.bundles.junit.jupiter)
	androidTestImplementation(libs.bundles.junit.jupiter)
}

android {
	namespace = "com.darkrockstudios.apps.hammer.android"
	compileSdk = libs.versions.android.sdk.compile.get().toInt()
	defaultConfig {
		applicationId = "com.darkrockstudios.apps.hammer.android"
		minSdk = libs.versions.android.sdk.min.get().toInt()
		targetSdk = libs.versions.android.sdk.target.get().toInt()
		versionCode = getVersionCode(libs.versions.app.get())
		versionName = libs.versions.app.get()

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		vectorDrawables {
			useSupportLibrary = true
		}
	}
	if (isFDroidBuild) {
		// Swap in the manifest that also declares the public-storage permissions.
		sourceSets.getByName("main").manifest.srcFile("src/fdroid/AndroidManifest.xml")
		// The dependency metadata blob is encrypted with a Google-only key, so it
		// can't be verified by F-Droid/IzzyOnDroid. Strip it from F-Droid outputs.
		dependenciesInfo {
			includeInApk = false
			includeInBundle = false
		}
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
				File("proguard-rules.pro")
			)
		}
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

aboutLibraries {
	export {
		prettyPrint = true
		excludeFields.addAll("generated")
	}
}