import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val app_version: String by extra

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.ktor)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.sqldelight)
	alias(libs.plugins.jetbrains.kover)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()
application {
	mainClass.set("com.darkrockstudios.apps.hammer.ApplicationKt")

	val isDevelopment: Boolean = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

sqldelight {
	databases {
		create("ServerDatabase") {
			packageName.set("com.darkrockstudios.apps.hammer")
			//dialect("app.cash.sqldelight:sqlite-3-35-dialect:$sqldelight_version")
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

repositories {
	google()
	mavenCentral()
}

tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = libs.versions.jvm.get()
}

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

	implementation(platform(libs.koin.bom))
	implementation(libs.bundles.koin.server)

	implementation(libs.okio)

	implementation(libs.sqldelight.driver)

	implementation(libs.kweb.core)
	implementation(libs.ktor.server.websockets)

	implementation(libs.tomlkt)
	implementation(libs.resources)

	testImplementation(libs.bundles.ktor.client)
	testImplementation(libs.ktor.serialization.kotlinx.json)

	testImplementation(libs.ktor.server.testsjvm)
	testImplementation(libs.kotlin.test.junit)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.mockk)
	testImplementation(libs.koin.test)
	testImplementation(libs.okio.fakefilesystem)
	testImplementation(libs.ktor.server.testshostjvm)
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
	includedSourceSets = listOf("test")
}