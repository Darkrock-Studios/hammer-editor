import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.kotlin.parcelize)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.buildconfig)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	androidLibrary {
		namespace = "com.darkrockstudios.apps.hammer.common"
		compileSdk = libs.versions.android.sdk.compile.get().toInt()
		minSdk = libs.versions.android.sdk.min.get().toInt()

		androidResources {
			enable = true
		}

		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}

		withHostTest {}
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
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
			}
		}

		val commonMain by getting {
			resources.srcDirs("resources")

			dependencies {
				api(project(":base"))

				api(libs.decompose)
				api(libs.napier)
				api(libs.coroutines.core)
				api(project.dependencies.platform(libs.koin.bom.get()))
				api(libs.koin.core)
				api(libs.okio)

				implementation(libs.bundles.ktor.client)
				implementation(libs.ktor.serialization.kotlinx.json)

				api(libs.serialization.core)
				api(libs.serialization.json)
				api(libs.kotlinx.datetime)
				api(libs.kotlinx.collections.immutable)
				implementation(libs.tomlkt)
				api(libs.bundles.essenty)
				implementation(libs.cache4k)
				implementation(libs.jetbrains.compose.runtime)
				implementation(libs.jetbrains.compose.components.resources)
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.aboutlibraries.core)
				implementation(libs.multiplatform.settings)
				implementation(libs.platform.spellcheckerkt)
				implementation(libs.kmp.zip)
				implementation(libs.kmp.zip.okio)
				implementation(libs.markdown)
				implementation(libs.rtf.reader)
				implementation(libs.rtf.writer)
				implementation(libs.xmlutil.core)
				implementation(libs.epub4kmp.core)
				implementation(libs.pdfkmp)
				implementation(libs.kotlinx.html)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.koin.test)
				implementation(libs.kotlin.reflect)
			}
		}
		val jvmMain by creating {
			dependsOn(commonMain)
		}
		val jvmTest by creating {
			dependsOn(commonTest)
			dependencies {
				// okio-fakefilesystem references the deprecated kotlinx.datetime.Clock typealias,
				// which double-binds during Kotlin/Native klib caching and breaks the iOS test
				// link. Keep it off the native test classpath - only JVM tests use FakeFileSystem.
				implementation(libs.okio.fakefilesystem)
			}
		}
		val androidMain by getting {
			dependsOn(jvmMain)
			dependencies {
				api(libs.androidx.core.ktx)
				api(libs.coroutines.android)
				implementation(libs.koin.android)
				implementation(libs.ktor.client.okhttp)
				implementation(libs.moko.permissions)
				implementation(libs.moko.permissions.storage)
				implementation(libs.androidx.security.crypto)
			}
		}
		val iosMain by getting {
			dependencies {
				api(libs.decompose)
				api(libs.bundles.essenty)
				api(libs.ktor.client.darwin)
			}
		}
		val iosTest by getting {
			dependencies {
				implementation(libs.multiplatform.settings.test)
			}
		}
		val androidHostTest by getting {
			dependencies {
				implementation(kotlin("test"))
			}
		}
		val desktopMain by getting {
			dependsOn(jvmMain)
			dependencies {
				implementation(libs.slf4j.simple)
				api(libs.serialization.jvm)
				api(libs.coroutines.swing)
				implementation(libs.appdirs)
				implementation(libs.ktor.client.java)
				implementation(libs.turbine)
			}
		}
		val desktopTest by getting {
			dependsOn(jvmTest)
			dependencies {
				implementation(libs.bundles.junit.jupiter)
				implementation(libs.coroutines.test)
				implementation(libs.mockk)
				implementation(libs.koin.test)
				implementation(libs.koin.test.junit5)
				implementation(compose.desktop.currentOs)
				implementation(libs.poi.ooxml)
				implementation(libs.ktor.client.mock)
			}
		}
	}
}

compose.resources {
	publicResClass = true
	packageOfResClass = "com.darkrockstudios.apps.hammer"
}

buildConfig {
	useKotlinOutput { internalVisibility = false }
	packageName("com.darkrockstudios.apps.hammer.common")

	val isDebug = project.findProperty("hammer.debug")?.toString()?.toBoolean() ?: false
	buildConfigField("Boolean", "DEBUG", isDebug.toString())

	// Mirrors the F-Droid detection in settings.gradle.kts so runtime code can branch
	// on the build channel (e.g. public-storage projects, which Google Play disallows).
	val isFDroid = project.findProperty("fdroid")?.toString()?.isNotEmpty() == true ||
		System.getenv("FDROID_BUILD") != null
	buildConfigField("Boolean", "FDROID", isFDroid.toString())
}

kover {
	reports {
		filters {
			includes {
				packages("com.darkrockstudios.apps.hammer.*")
			}
			excludes {
				packages(
					"com.darkrockstudios.apps.hammer.util.*",
					"com.darkrockstudios.apps.hammer.parcelize.*",
					"com.darkrockstudios.apps.hammer.fileio.*",
				)
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