import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.library)
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.buildconfig)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

repositories {
    mavenCentral()
}

kotlin {
    androidTarget()
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
		}
	}
	iosX64()
	iosArm64()
	iosSimulatorArm64()

	applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
				implementation(libs.serialization.core)
				implementation(libs.coroutines.core)
	            implementation(libs.kotlinx.datetime)
				implementation(libs.serialization.json)
				//implementation("org.kotlincrypto.endians:endians:0.1.0")
				//api("io.getstream:stream-result:1.1.0")
				api(libs.uuid)
				api(libs.korlibs.krypto)
				//api("com.goncalossilva:murmurhash:0.4.0")
				api(libs.cryptohash)
				api(libs.korlibs.korio)
				implementation(libs.okio)
				implementation(libs.tomlkt)
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

android {
	namespace = "com.darkrockstudios.apps.hammer.base"
	compileSdk = libs.versions.android.sdk.compile.get().toInt()
    defaultConfig {
		minSdk = libs.versions.android.sdk.min.get().toInt()
	    lint.targetSdk = libs.versions.android.sdk.target.get().toInt()
	}

    compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
		targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get().toInt())
    }
}

buildConfig {
    className("BuildMetadata")
    useKotlinOutput { internalVisibility = false }

	buildConfigField("String", "APP_VERSION", "\"${libs.versions.app.get()}\"")
	buildConfigField("String", "DATA_VERSION", "\"${libs.versions.data.version.get()}\"")
}

val GIT_TASK_NAME = "install-git-hooks"
tasks.register<Copy>(GIT_TASK_NAME) {
    from(layout.projectDirectory.file("../.gitHooks/pre-commit"))
    into(layout.projectDirectory.dir("../.git/hooks"))

    doLast {
        val file = layout.projectDirectory.file("../.git/hooks")
        file.asFile.setExecutable(true)
    }
}

afterEvaluate {
    val gitTask = tasks[GIT_TASK_NAME]
    for (task in tasks) {
        if (task != gitTask)
            task.dependsOn(gitTask)
    }
}