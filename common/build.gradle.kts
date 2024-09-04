import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.parcelize)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.moko.resources)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	androidTarget()
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	listOf(
		iosX64(),
		iosArm64(),
		iosSimulatorArm64()
	).forEach { iosTarget ->
		iosTarget.binaries.framework {
			baseName = "Hammer"
			//isStatic = true
			//transitiveExport = true
			export(libs.decompose)
			export(libs.essenty.lifecycle)
			export(libs.coroutines.core)
			export(libs.moko.resources)
			export(libs.moko.graphics)
			export(libs.napier)
		}
	}

	applyDefaultHierarchyTemplate()

	sourceSets {
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
				implementation(libs.tomlkt)
				api(libs.bundles.essenty)
				implementation(libs.cache4k)
				api(libs.moko.resources)
				api(libs.moko.graphics)
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.fluidsonic.locale)
				implementation(libs.aboutlibraries.core)
				implementation(libs.multiplatform.settings)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.koin.test)
				implementation(libs.okio.fakefilesystem)
				implementation(libs.kotlin.reflect)
				implementation(libs.moko.resources.test)
			}
		}
		val androidMain by getting {
			dependencies {
				api(libs.androidx.core.ktx)
				api(libs.coroutines.android)
				implementation(libs.koin.android)
				implementation(libs.ktor.client.okhttp)
				implementation(libs.moko.permissions)
			}
		}
		val iosMain by getting {
			dependencies {
				api(libs.decompose)
				api(libs.bundles.essenty)
				api(libs.moko.resources)
				api(libs.ktor.client.darwin)
				// TODO Remove this when there is a better way to read zip files on iOS
				// this library is quite big
				implementation(libs.korge.core)
			}
		}
		val iosTest by getting
		val androidUnitTest by getting {
			dependencies {
			}
		}
		val desktopMain by getting {
			dependencies {
				implementation(libs.slf4j.simple)
				api(libs.serialization.jvm)
				api(libs.coroutines.swing)
				implementation(libs.appdirs)
				api(libs.moko.resources.compose)
				implementation(libs.ktor.client.java)
			}
		}
		val desktopTest by getting {
			dependencies {
				implementation(libs.coroutines.test)
				implementation(libs.mockk)
				implementation(libs.koin.test)
			}
		}
	}
}

multiplatformResources {
	resourcesPackage.set("com.darkrockstudios.apps.hammer")
}

android {
	namespace = "com.darkrockstudios.apps.hammer.common"
	compileSdk = libs.versions.android.sdk.compile.get().toInt()
	sourceSets {
		named("main") {
			manifest.srcFile("src/androidMain/AndroidManifest.xml")
			res.srcDirs(
				"resources",
				"src/androidMain/res",
				"src/commonMain/resources",
				// https://github.com/icerockdev/moko-resources/issues/353#issuecomment-1179713713
				File(layout.buildDirectory.asFile.get(), "generated/moko/androidMain/res")
			)
		}
	}
	defaultConfig {
		minSdk = libs.versions.android.sdk.min.get().toInt()
		lint.targetSdk = libs.versions.android.sdk.target.get().toInt()
	}
	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
	}
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