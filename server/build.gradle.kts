import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

val app_version: String by extra

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.ktor)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.sqldelight)
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

ktor {
	fatJar {
		archiveFileName.set("hammer-server.jar")
	}
}

val isDevelopment: Boolean = project.ext.has("development")

repositories {
	google()
	mavenCentral()
}

kotlin {
	jvm {
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		mainRun {
			mainClass.set("com.darkrockstudios.apps.hammer.ApplicationKt")
		}
		withJava()
	}

	jvmToolchain(libs.versions.jvm.get().toInt())

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
				optIn("io.ktor.utils.io.ExperimentalKtorApi")
			}
		}

		val jvmMain by getting {
			dependencies {
				implementation(project(":base"))

				implementation(libs.coroutines.core)
				implementation(libs.coroutines.jdk8)
				implementation(libs.serialization.jvm)
				implementation(libs.kotlinx.datetime)
				implementation(libs.kotlinx.cli)

				implementation(libs.bundles.ktor.server)
				implementation(libs.ktor.network.tlscertificates)

				implementation(libs.slf4j.simple)
				//implementation(libs.logback.classic)

				implementation(project.dependencies.platform(libs.koin.bom))
				implementation(libs.bundles.koin.server)

				implementation(libs.okio)

				implementation(libs.sqldelight.driver)

				implementation(libs.kweb.core)
				implementation(libs.ktor.server.websockets)


				implementation(libs.ktor.server.mustache)
				implementation(libs.ktor.server.html.builder)

				implementation(libs.ktor.server.status.pages)

				implementation(libs.ktor.htmx)
				implementation(libs.ktor.htmx.html)
				implementation(libs.ktor.server.htmx)

				implementation(libs.tomlkt)
				implementation(libs.resources)

//				implementation(libs.cryptography.core)
//				implementation(libs.cryptography.provider.jdk)
				implementation(libs.kache)
			}
		}

		val jvmTest by getting {
			dependencies {
				implementation(libs.bundles.ktor.client)
				implementation(libs.ktor.serialization.kotlinx.json)

				implementation(libs.ktor.server.test.host)
				implementation(libs.coroutines.test)
				implementation(libs.mockk)
				implementation(libs.koin.test)
				implementation(libs.okio.fakefilesystem)
				implementation(libs.bundles.junit.jupiter)
				runtimeOnly(libs.junit.jupiter.engine)
				runtimeOnly(libs.junit.platform.launcher)
			}
		}
	}
}

sqldelight {
	databases {
		create("ServerDatabase") {
			packageName.set("com.darkrockstudios.apps.hammer.database")
			//dialect("app.cash.sqldelight:sqlite-3-35-dialect:$sqldelight_version")
			version = 2
			schemaOutputDirectory.set(project.file("build/generated/sqldelight"))
			srcDirs.setFrom("src/jvmMain/sqldelight")
		}
	}
}

kover {
	reports {
		filters {
			includes {
				packages("com.darkrockstudios.apps.hammer.*")
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
		"kotlin.test.assertNull",
		"kotlin.test.assertContains",
	)
	includedSourceSets = listOf("jvmTest")
}
