plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.parcelize)
	alias(libs.plugins.parcelize.darwin)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.moko.resources)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	androidTarget()
	jvm("desktop") {
		compilations.all {
			kotlinOptions.jvmTarget = libs.versions.jvm.get()
		}
	}
	ios {
		binaries {
			framework {
				baseName = "Hammer"
				//transitiveExport = true
				export(libs.decompose)
				export(libs.essenty)
				//export(libs.parcelize.darwin.runtime)
				export(libs.coroutines.core)
				export(libs.moko.resources)
				export(libs.moko.graphics)
				export(libs.napier)
			}
		}
	}

	sourceSets {
		val commonMain by getting {
			resources.srcDirs("resources")

			dependencies {
				api(project(":base"))

				api(libs.decompose)
				implementation(libs.essenty.parcelable)
				api(libs.napier)
				api(libs.coroutines.core)
				api(platform(libs.koin.bom.get()))
				api(libs.koin.core)
				api(libs.okio)

				implementation(libs.bundles.ktor.client)
				implementation(libs.ktor.serialization.kotlinx.json)

				api(libs.serialization.core)
				api(libs.serialization.json)
				api(libs.datetime)
				implementation(libs.tomlkt)
				api(libs.essenty)
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
				dependsOn(commonMain) // TODO https://github.com/icerockdev/moko-resources/issues/557
				api(libs.androidx.core.ktx)
				api(libs.coroutines.android)
				implementation(libs.koin.android)
				implementation(libs.ktor.client.okhttp)
			}
		}
		val iosMain by getting {
			dependencies {
				dependsOn(commonMain) // TODO https://github.com/icerockdev/moko-resources/issues/557
				//implementation(libs.parcelize.darwin.runtime)
				api(libs.decompose)
				api(libs.essenty)
				api(libs.moko.resources)
				api(libs.ktor.client.darwin)
			}
		}
		val iosTest by getting
		val androidUnitTest by getting {
			dependencies {
			}
		}
		val desktopMain by getting {
			dependencies {
				dependsOn(commonMain) // TODO https://github.com/icerockdev/moko-resources/issues/557
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
	multiplatformResourcesPackage = "com.darkrockstudios.apps.hammer"
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
				File(buildDir, "generated/moko/androidMain/res")
			)
		}
	}
	defaultConfig {
		minSdk = libs.versions.android.sdk.min.get().toInt()
		targetSdk = libs.versions.android.sdk.target.get().toInt()
	}
	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
	}
}

koverReport {
	defaults {
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