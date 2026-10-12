import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
	}
	androidLibrary {
		namespace = "com.darkrockstudios.apps.hammer.plugins.wasmhost"
		compileSdk = libs.versions.android.sdk.compile.get().toInt()
		minSdk = libs.versions.android.sdk.min.get().toInt()

		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	iosArm64()
	iosSimulatorArm64()

	applyDefaultHierarchyTemplate()

	sourceSets {
		val commonMain by getting {
			dependencies {
				api(project(":operations"))
				implementation(libs.chasm)
				// The runtime store and guest heap, to cap the memory GC-language plugins allocate.
				implementation(libs.chasm.core)
				implementation(libs.chasm.gc)
				implementation(libs.tomlkt)
			}
		}
		// One file for both JVM targets, which the default hierarchy gives no source set of their own.
		val desktopMain by getting { kotlin.srcDir("src/jvmSharedMain/kotlin") }
		val androidMain by getting { kotlin.srcDir("src/jvmSharedMain/kotlin") }
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
			}
		}
		val desktopTest by getting {
			dependencies {
				implementation(libs.bundles.junit.jupiter)
				implementation(libs.coroutines.test)
				implementation(libs.okio.fakefilesystem)
			}
		}
	}
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
	functions = listOf(
		"kotlin.assert",
		"kotlin.test.assertTrue",
		"kotlin.test.assertEquals",
		"kotlin.test.assertNull"
	)
	includedSourceSets = listOf("commonTest", "desktopTest")
}

// A Kotlin/Wasm plugin holds everything a call allocates until the call returns, and may take up to half
// the heap: the whole-book plugin tests and benchmarks need room.
tasks.withType<Test>().configureEach {
	maxHeapSize = "2g"
}
