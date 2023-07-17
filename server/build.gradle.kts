import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlin_version: String by project
val ktor_version: String by project
val logback_version: String by project
val coroutines_version: String by extra
val mockk_version: String by extra
val sqldelight_version: String by extra
val datetime_version: String by extra
val kotlinx_serialization_version: String by extra
val jvm_version: String by extra
val ktoml_version: String by extra
val app_version: String by extra

plugins {
	kotlin("jvm")
	id("io.ktor.plugin")
	id("org.jetbrains.kotlin.plugin.serialization")
	id("app.cash.sqldelight")
	id("org.jetbrains.kotlinx.kover")
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

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutines_version")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinx_serialization_version")
	implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetime_version")
	implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")

	implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
	implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-compression-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-caching-headers-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
	implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
	implementation("io.ktor:ktor-network-tls-certificates:$ktor_version")
	implementation("io.ktor:ktor-server-http-redirect:$ktor_version")

	implementation("org.slf4j:slf4j-simple:2.0.6")

	implementation(libs.bundles.koin.server)

	implementation(libs.okio)

	implementation("app.cash.sqldelight:sqlite-driver:$sqldelight_version")
	implementation("app.cash.sqldelight:primitive-adapters:$sqldelight_version")

	implementation("io.kweb:kweb-core:1.4.6")
	implementation("io.ktor:ktor-server-websockets:$ktor_version")

	implementation("com.akuleshov7:ktoml-core:$ktoml_version")

	testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
	testImplementation("io.mockk:mockk:$mockk_version")
	testImplementation(libs.koin.test)
	testImplementation(libs.okio.fakefilesystem)
	//testImplementation("io.ktor:ktor-server-test-host-jvm:2.2.4")
}
