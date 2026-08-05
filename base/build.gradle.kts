import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.buildconfig)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

repositories {
	mavenCentral()
}

kotlin {
	androidLibrary {
		namespace = "com.darkrockstudios.apps.hammer.base"
		compileSdk = libs.versions.android.sdk.compile.get().toInt()
		minSdk = libs.versions.android.sdk.min.get().toInt()

		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}
	jvm("desktop")
	iosArm64()
	iosSimulatorArm64()

	jvmToolchain(libs.versions.jvm.get().toInt())

	applyDefaultHierarchyTemplate()

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.io.encoding.ExperimentalEncodingApi")
				optIn("kotlin.uuid.ExperimentalUuidApi")
			}
		}

		val commonMain by getting {
			dependencies {
				api(project.dependencies.platform(libs.koin.bom.get()))
				api(libs.koin.core)
				implementation(libs.serialization.core)
				implementation(libs.coroutines.core)
				implementation(libs.serialization.json)
				//implementation("org.kotlincrypto.endians:endians:0.1.0")
				//api("io.getstream:stream-result:1.1.0")
				api(libs.korlibs.krypto)
				//api("com.goncalossilva:murmurhash:0.4.0")
				api(libs.cryptohash)
				api(libs.korlibs.korio)
				implementation(libs.okio)
				implementation(libs.tomlkt)
				implementation(libs.markdown)
				implementation(libs.kotlin.multiplatform.diff)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.kotlin.reflect)
				implementation(libs.okio)
			}
		}
	}
}

buildConfig {
	className("BuildMetadata")
	useKotlinOutput { internalVisibility = false }

	buildConfigField("String", "APP_VERSION", "\"${libs.versions.app.get()}\"")
	buildConfigField("String", "DATA_VERSION", "\"${libs.versions.data.version.get()}\"")
}

val GIT_TASK_NAME = "install-git-hooks"
val gitDir = layout.projectDirectory.file("../.git").asFile
// In a git worktree `.git` is a file pointer rather than the hooks dir, so
// register a no-op — hooks are installed when the main checkout builds.
if (gitDir.isDirectory) {
	tasks.register<Copy>(GIT_TASK_NAME) {
		from(layout.projectDirectory.file("../.gitHooks/pre-commit"))
		into(layout.projectDirectory.dir("../.git/hooks"))

		doLast {
			val file = layout.projectDirectory.file("../.git/hooks")
			file.asFile.setExecutable(true)
		}
	}
} else {
	tasks.register(GIT_TASK_NAME)
}

afterEvaluate {
	val gitTask = tasks[GIT_TASK_NAME]
	for (task in tasks) {
		if (task != gitTask)
			task.dependsOn(gitTask)
	}
}