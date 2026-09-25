import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

// Desktop only: MCP serves over the CLI, which only the desktop app has.
kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
	}
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	sourceSets {
		val desktopMain by getting {
			dependencies {
				api(project(":operations"))
				implementation(libs.mcp.kotlin.server)
				implementation(libs.tomlkt)
			}
		}
		val desktopTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.bundles.junit.jupiter)
				implementation(libs.coroutines.test)
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
	includedSourceSets = listOf("desktopTest")
}
