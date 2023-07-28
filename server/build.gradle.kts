import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String by project
val logback_version: String by project
val mockk_version: String by extra
val kotlinx_serialization_version: String by extra
val app_version: String by extra

plugins {
	alias(libs.plugins.kotlin.jvm)
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
	filters {
		classes {
			includes += "com.darkrockstudios.apps.hammer.*"
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
	implementation(libs.datetime)
	implementation(libs.kotlinx.cli)

	implementation(libs.ktor.server.contentnegotiationsjvm)
	implementation(libs.ktor.server.corejvm)
	implementation(libs.ktor.server.serializationkotlinxjsonjvm)
	implementation(libs.ktor.server.callloggingjvm)
	implementation(libs.ktor.server.defaultheadersjvm)
	implementation(libs.ktor.server.compressionjvm)
	implementation(libs.ktor.server.cachingheadersjvm)
	implementation(libs.ktor.server.authjvm)
	implementation(libs.ktor.server.nettyjvm)
	implementation(libs.ktor.server.httpredirect)
	implementation(libs.ktor.network.tlscertificates)

	implementation(libs.slf4j.simple)

	implementation(libs.bundles.koin.server)

	implementation(libs.okio)

	implementation(libs.sqldelight.driver)
	implementation(libs.sqldelight.driver)

	implementation(libs.kweb.core)
	implementation(libs.ktor.server.websockets)

	implementation(libs.ktoml)

	testImplementation(libs.ktor.server.testsjvm)
	testImplementation(libs.kotlin.test.junit)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.mockk)
	testImplementation(libs.koin.test)
	testImplementation(libs.okio.fakefilesystem)
	testImplementation(libs.ktor.server.testshostjvm)
}
