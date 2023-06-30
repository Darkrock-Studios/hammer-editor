val app_version: String by extra
val jvm_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val kotlin_version: String by extra
val coroutines_version: String by extra
val kotlinx_serialization_version: String by extra
val decompose_version: String by extra
val koin_version: String by extra
val okio_version: String by extra
val essenty_version: String by extra
val mockk_version: String by extra
val moko_resources_version: String by extra
val datetime_version: String by extra
val ktor_version: String by extra
val json_version: String by extra
val atomicfu_version: String by extra
val ktoml_version: String by extra

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
	id("com.android.library")
	id("kotlin-parcelize")
	//id("parcelize-darwin")
	id("org.jetbrains.kotlinx.kover")
	id("dev.icerock.mobile.multiplatform-resources")
}

group = "com.darkrockstudios.apps.hammer"
version = app_version

kotlin {
	android()
	jvm("desktop") {
		compilations.all {
			kotlinOptions.jvmTarget = jvm_version
		}
	}
	ios {
		binaries {
			framework {
				baseName = "Hammer"
				//transitiveExport = true
				export("com.arkivanov.decompose:decompose:$decompose_version")
				// This isn't working for some reason, once it is remove transitiveExport
				export("com.arkivanov.essenty:lifecycle:$essenty_version")
				export("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
				export("dev.icerock.moko:resources:$moko_resources_version")
				export(libs.napier)
			}
		}
	}

	sourceSets {
		val commonMain by getting {
			resources.srcDirs("resources")

			dependencies {
				api(project(":base"))

				api("com.arkivanov.decompose:decompose:$decompose_version")
				api(libs.napier)
				api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
				api("io.insert-koin:koin-core:$koin_version")
				api("com.squareup.okio:okio:$okio_version")

				api("io.ktor:ktor-client-core:$ktor_version")
				implementation("io.ktor:ktor-client-auth:$ktor_version")
				implementation("io.ktor:ktor-client-logging:$ktor_version")
				implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
				implementation("io.ktor:ktor-client-encoding:$ktor_version")
				implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

				api("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinx_serialization_version")
				api("org.jetbrains.kotlinx:kotlinx-datetime:$datetime_version")
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$json_version")
				implementation("com.akuleshov7:ktoml-core:$ktoml_version")
				api("com.arkivanov.essenty:lifecycle:$essenty_version")
				implementation("io.github.reactivecircus.cache4k:cache4k:0.9.0")
				api("dev.icerock.moko:resources:$moko_resources_version")
				implementation("org.jetbrains.kotlinx:atomicfu:$atomicfu_version")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				//implementation("io.insert-koin:koin-test:$koin_version")
				implementation("com.squareup.okio:okio-fakefilesystem:$okio_version")
				implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
				implementation("dev.icerock.moko:resources-test:$moko_resources_version")
			}
		}
		val androidMain by getting {
			dependencies {
				//api("androidx.appcompat:appcompat:1.5.1")
				api("androidx.core:core-ktx:1.10.0")
				api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")
				api("io.insert-koin:koin-android:$koin_version")
				implementation("io.ktor:ktor-client-okhttp:$ktor_version")
			}
		}
		val iosMain by getting {
			dependencies {
				api("com.arkivanov.decompose:decompose:$decompose_version")
				api("com.arkivanov.essenty:lifecycle:$essenty_version")
				api("dev.icerock.moko:resources:$moko_resources_version")
				api("io.ktor:ktor-client-darwin:$ktor_version")
			}
		}
		val iosTest by getting
		val androidUnitTest by getting {
			dependencies {
			}
		}
		val desktopMain by getting {
			dependencies {
				implementation("org.slf4j:slf4j-simple:2.0.6")
				api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinx_serialization_version")
				api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutines_version")
				implementation("net.harawata:appdirs:1.2.1")
				api("dev.icerock.moko:resources-compose:$moko_resources_version")
				//implementation("io.ktor:ktor-client-curl:$ktor_version")
				implementation("io.ktor:ktor-client-java:$ktor_version")
			}
		}
		val desktopTest by getting {
			dependencies {
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
				implementation("io.mockk:mockk:$mockk_version")
				implementation("io.insert-koin:koin-test:$koin_version")
			}
		}
	}
}

multiplatformResources {
	multiplatformResourcesPackage = "com.darkrockstudios.apps.hammer"
}

android {
	namespace = "com.darkrockstudios.apps.hammer.common"
	compileSdk = android_compile_sdk.toInt()
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
		minSdk = android_min_sdk.toInt()
		targetSdk = android_target_sdk.toInt()
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