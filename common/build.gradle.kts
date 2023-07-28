plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.library)
	id("kotlin-parcelize")
	//id("parcelize-darwin")
	id("org.jetbrains.kotlinx.kover")
	id("dev.icerock.mobile.multiplatform-resources")
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	android()
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
				// This isn't working for some reason, once it is remove transitiveExport
				export(libs.essenty)
				export(libs.coroutines.core)
				export(libs.moko.resources)
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
				api(libs.napier)
				api(libs.coroutines.core)
				api(libs.koin.core)
				api(libs.okio)

				api(libs.ktor.client.core)
				implementation(libs.ktor.client.auth)
				implementation(libs.ktor.client.logging)
				implementation(libs.ktor.client.content.negotiation)
				implementation(libs.ktor.client.encoding)
				implementation(libs.ktor.serialization.kotlinx.json)

				api(libs.serialization.core)
				api(libs.serialization.json)
				api(libs.datetime)
				implementation(libs.ktoml)
				api(libs.essenty)
				implementation(libs.cache4k)
				api(libs.moko.resources)
				implementation(libs.kotlinx.atomicfu)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.koin.test)
				implementation(libs.okio.fakefilesystem)
				implementation(libs.kotlin.reflect)
				implementation(libs.moko.resources)
			}
		}
		val androidMain by getting {
			dependencies {
				//api("androidx.appcompat:appcompat:1.5.1")
				api(libs.androidx.core.ktx)
				api(libs.coroutines.android)
				implementation(libs.koin.android)
				implementation(libs.ktor.client.okhttp)
			}
		}
		val iosMain by getting {
			dependencies {
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
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kover {
	filters {
		classes {
			includes += "com.darkrockstudios.apps.hammer.*"
			excludes += listOf(
				"com.darkrockstudios.apps.hammer.util.*",
				"com.darkrockstudios.apps.hammer.parcelize.*",
				"com.darkrockstudios.apps.hammer.fileio.*",
			)
		}
	}
}