val jvm_version: String by extra
val app_version: String by extra
val android_compile_sdk: String by extra
val android_target_sdk: String by extra
val android_min_sdk: String by extra
val kotlinx_serialization_version: String by extra
val datetime_version: String by extra
val coroutines_version: String by extra
val json_version: String by extra
val korio_version: String by extra

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
    id("com.github.gmazzo.buildconfig")
}

group = "com.darkrockstudios.apps.hammer"
version = app_version

repositories {
    mavenCentral()
}

kotlin {
    android()
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = jvm_version
        }
    }
    ios {

    }

    sourceSets {
        val commonMain by getting {
            dependencies {
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinx_serialization_version")
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
				implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetime_version")
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$json_version")
                //implementation("org.kotlincrypto.endians:endians:0.1.0")
                //api("io.getstream:stream-result:1.1.0")
				api("com.benasher44:uuid:0.7.0")
                api("com.soywiz.korlibs.krypto:krypto:$korio_version")
				//api("com.goncalossilva:murmurhash:0.4.0")
				api("com.appmattus.crypto:cryptohash:0.10.1")
				api("com.soywiz.korlibs.korio:korio:$korio_version")
			}
        }
    }
}

android {
    namespace = "com.darkrockstudios.apps.hammer.base"
    compileSdk = android_compile_sdk.toInt()
    defaultConfig {
        minSdk = android_min_sdk.toInt()
        targetSdk = android_target_sdk.toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

buildConfig {
    className("BuildMetadata")
    useKotlinOutput { internalVisibility = false }

    buildConfigField("String", "APP_VERSION", "\"$app_version\"")
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