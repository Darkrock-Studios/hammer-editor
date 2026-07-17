import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.jetbrains.compose)
	alias(libs.plugins.compose.compiler)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())

	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
			}
		}

		val jvmTest by getting {
			dependencies {
				implementation(project(":base"))
				implementation(project(":common"))

				implementation(libs.bundles.junit.jupiter)
				implementation(libs.coroutines.test)
				implementation(libs.mockk)
				// :common consumes Compose Resources as `implementation` so we don't
				// inherit its compile classpath; depend on it directly for StrRes mocks.
				implementation("org.jetbrains.compose.components:components-resources:${libs.versions.jetbrains.compose.get()}")
				implementation(libs.okio)
				implementation(libs.ktor.client.java)
				implementation(libs.kotlinx.datetime)
				implementation(libs.serialization.json)

				implementation(project.dependencies.platform(libs.koin.bom))
				implementation(libs.koin.core)
				implementation(libs.koin.test)
				implementation(libs.koin.test.junit5)

				implementation(libs.napier)
				implementation(libs.slf4j.simple)
				// Brings in skiko native libs needed by transitively loaded Compose
				// runtime classes when SceneEditorRepository is constructed.
				implementation(compose.desktop.currentOs)

				runtimeOnly(libs.junit.jupiter.engine)
				runtimeOnly(libs.junit.platform.launcher)
			}
		}
	}
}

// testFixtures() helper is on the project-level DependencyHandler, not the KMP
// source-set DSL — declare it here against the jvmTest configuration directly.
dependencies {
	"jvmTestImplementation"(testFixtures(project(":server")))
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	// These tests share JVM-global state (Koin GlobalContext, Dispatchers.setMain),
	// so they must run one at a time.
	maxParallelForks = 1
}
