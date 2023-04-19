val kotlin_version: String by project
val ktor_version: String by project
val logback_version: String by project
val koin_version: String by extra
val okio_version: String by extra
val coroutines_version: String by extra
val mockk_version: String by extra
val sqldelight_version: String by extra
val datetime_version: String by extra
val kotlinx_serialization_version: String by extra

plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
    id("org.jetbrains.kotlinx.kover")
}

group = "com.darkrockstudios.apps.hammer"
version = "0.0.1"
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

dependencies {
    implementation(project(":base"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
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

	implementation("ch.qos.logback:logback-classic:$logback_version")
	implementation("org.slf4j:slf4j-simple:2.0.6")

	implementation("io.insert-koin:koin-core:$koin_version")
	implementation("io.insert-koin:koin-logger-slf4j:3.3.1")
	implementation("io.insert-koin:koin-ktor:3.3.1")

	implementation("com.squareup.okio:okio:$okio_version")

	implementation("app.cash.sqldelight:sqlite-driver:$sqldelight_version")
	implementation("app.cash.sqldelight:primitive-adapters:$sqldelight_version")

	testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
	testImplementation("io.mockk:mockk:$mockk_version")
	testImplementation("io.insert-koin:koin-test:$koin_version")
	testImplementation("com.squareup.okio:okio-fakefilesystem:$okio_version")
	//testImplementation("io.ktor:ktor-server-test-host-jvm:2.2.4")
}
