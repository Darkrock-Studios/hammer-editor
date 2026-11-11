import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertCompilationFilter

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
	android {
		namespace = "com.darkrockstudios.apps.hammer.operations"
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
		getByName("commonMain") {
			dependencies {
				api(project(":common"))
				implementation(libs.tomlkt)
			}
		}
		getByName("commonTest") {
			dependencies {
				implementation(kotlin("test"))
			}
		}
		getByName("desktopTest") {
			dependencies {
				implementation(libs.bundles.junit.jupiter)
				implementation(libs.coroutines.test)
				implementation(libs.koin.test)
				implementation(libs.okio.fakefilesystem)
				implementation(libs.mockk)
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
	compilationFilter = object : PowerAssertCompilationFilter {
		override fun check(compilation: KotlinCompilation<*>) =
			compilation.defaultSourceSet.name in setOf("commonTest", "desktopTest")
	}
}
