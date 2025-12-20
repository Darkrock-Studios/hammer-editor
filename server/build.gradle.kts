import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

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

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xopt-in=kotlin.io.encoding.ExperimentalEncodingApi",
			"-Xopt-in=kotlin.uuid.ExperimentalUuidApi",
			"-Xopt-in=io.ktor.utils.io.ExperimentalKtorApi",
		)
	}
}

sqldelight {
	databases {
		create("ServerDatabase") {
			packageName.set("com.darkrockstudios.apps.hammer.database")
			//dialect("app.cash.sqldelight:sqlite-3-35-dialect:$sqldelight_version")
			version = 3
			schemaOutputDirectory.set(project.file("build/generated/sqldelight"))
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

	implementation(libs.ktor.server.websockets)
	implementation(libs.ktor.server.mustache)
	implementation(libs.ktor.server.html.builder)

	implementation(libs.ktor.server.status.pages)

	implementation(libs.ktor.htmx)
	implementation(libs.ktor.htmx.html)
	implementation(libs.ktor.server.htmx)

	implementation(libs.tomlkt)
	implementation(libs.resources)

//	implementation(libs.cryptography.core)
//	implementation(libs.cryptography.provider.jdk)
	implementation(libs.kache)

	testImplementation(libs.bundles.ktor.client)
	testImplementation(libs.ktor.serialization.kotlinx.json)

	testImplementation(libs.ktor.server.test.host)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.mockk)
	testImplementation(libs.koin.test)
	testImplementation(libs.okio.fakefilesystem)
	testImplementation(libs.bundles.junit.jupiter)
	testRuntimeOnly(libs.junit.jupiter.engine)
	testRuntimeOnly(libs.junit.platform.launcher)
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