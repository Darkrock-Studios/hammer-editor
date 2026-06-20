import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

val app_version: String by extra

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.powerassert)
	alias(libs.plugins.ktor)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.sqldelight)
	alias(libs.plugins.jetbrains.kover)
	`java-test-fixtures`
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
		// Production database — single source of truth going forward (PostgreSQL).
		create("ServerDatabase") {
			packageName.set("com.darkrockstudios.apps.hammer.database")
			dialect(libs.sqldelight.postgresql.dialect.get().toString())
			srcDirs("src/main/sqldelight")
			version = 5
			schemaOutputDirectory.set(project.file("build/generated/sqldelight"))
		}
		// Read-only legacy database — used ONLY by the one-time SQLite-to-Postgres migrator.
		// Marked for removal in a future release once production has migrated.
		create("LegacySqliteDatabase") {
			packageName.set("com.darkrockstudios.apps.hammer.database.legacy")
			srcDirs("src/main/sqldelight-legacy-sqlite")
			version = 5
			schemaOutputDirectory.set(project.file("build/generated/sqldelight-legacy"))
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
	implementation(libs.clikt)

	implementation(libs.bundles.ktor.server)
	implementation(libs.ktor.server.hsts)
	implementation(libs.ktor.network.tlscertificates)
	implementation(libs.bouncycastle.bcpkix)
	implementation(libs.jakarta.mail)

	// Logback is the active SLF4J binding (replaces slf4j-simple) so logback.xml's
	// appenders apply — including the in-memory RingBufferLogAppender for the admin log viewer.
	implementation(libs.logback.classic)

	implementation(project.dependencies.platform(libs.koin.bom))
	implementation(libs.bundles.koin.server)

	implementation(libs.okio)

	// SqlDelight: legacy SQLite driver (read-only, used by the one-time migrator)
	// and the JDBC driver (used by the production PostgreSQL backends).
	implementation(libs.sqldelight.driver)
	implementation(libs.sqldelight.jdbc.driver)

	// PostgreSQL backends — embedded (Zonky) for local/personal installs, remote for production.
	implementation(libs.postgresql.jdbc)
	implementation(libs.hikaricp)
	implementation(libs.embedded.postgres)
	implementation(platform(libs.embedded.postgres.binaries.bom))
	implementation(libs.bundles.embedded.postgres.binaries)

	implementation(libs.ktor.server.websockets)
	implementation(libs.ktor.server.mustache)
	implementation(libs.ktor.server.html.builder)

	implementation(libs.ktor.server.status.pages)

	implementation(libs.ktor.htmx)
	implementation(libs.ktor.htmx.html)
	implementation(libs.ktor.server.htmx)

	// Ktor HTTP client for Patreon API calls
	implementation(libs.ktor.client.core)
	implementation(libs.ktor.client.java)
	implementation(libs.ktor.client.content.negotiation)
	implementation(libs.ktor.client.logging)

	implementation(libs.tomlkt)
	implementation(libs.resources)
	implementation(libs.markdown)
	implementation(libs.owasp.html.sanitizer)

//	implementation(libs.cryptography.core)
//	implementation(libs.cryptography.provider.jdk)
	implementation(libs.kache)
	implementation(libs.argon2.jvm)

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

	// Testcontainers — used only for the RemotePostgresDatabase smoke test.
	// Other tests use the in-process embedded Postgres (fast, no Docker dep).
	testImplementation(platform(libs.testcontainers.bom))
	testImplementation(libs.testcontainers.postgresql)
	testImplementation(libs.testcontainers.junit.jupiter)

	// testFixtures exposes the reusable E2E harness (EndToEndTest, E2eTestData,
	// SqliteTestDatabase) to both :server's own tests and the :integrationTests module.
	testFixturesApi(project(":base"))
	testFixturesApi(libs.bundles.ktor.client)
	testFixturesApi(libs.ktor.serialization.kotlinx.json)
	testFixturesApi(libs.ktor.client.java)
	testFixturesApi(libs.bundles.ktor.server)
	testFixturesApi(libs.okio)
	testFixturesApi(libs.okio.fakefilesystem)
	testFixturesApi(libs.sqldelight.driver)
	testFixturesApi(libs.sqldelight.jdbc.driver)
	testFixturesApi(libs.bundles.junit.jupiter)
	testFixturesApi(libs.coroutines.core)
	testFixturesApi(libs.serialization.json)
	testFixturesApi(libs.kotlinx.datetime)
	testFixturesApi(project.dependencies.platform(libs.koin.bom))
	testFixturesApi(libs.koin.core)
	testFixturesApi(libs.embedded.postgres)
	testFixturesApi(platform(libs.embedded.postgres.binaries.bom))
	testFixturesApi(libs.bundles.embedded.postgres.binaries)
}

// Runs the reviewer-editor JavaScript unit tests with Node's built-in test runner.
// Kept separate from the Kotlin `test` task so contributors without Node can still
// build; CI runs it as its own explicit gate (see .github/workflows/build.yml).
val jsTest by tasks.registering(Exec::class) {
	group = "verification"
	description = "Runs the reviewer-editor JavaScript unit tests (requires Node)."
	workingDir = projectDir
	commandLine("node", "--test", "src/jsTest/**/*.test.js")
	inputs.dir("src/main/resources/assets/js")
	inputs.dir("src/jsTest")
	// Node availability/version isn't tracked as an input, so don't cache as up-to-date.
	outputs.upToDateWhen { false }
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