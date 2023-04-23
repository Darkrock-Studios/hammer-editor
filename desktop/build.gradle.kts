import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val app_version: String by extra
val jvm_version: String by extra

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlinx.kover")
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = app_version


kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = jvm_version
        }
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":composeUi"))
                implementation(compose.preview)
                implementation(compose.desktop.currentOs)
                implementation("com.github.weisj:darklaf-core:3.0.2")
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "com.darkrockstudios.apps.hammer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            includeAllModules = true
            packageName = "hammer"
            packageVersion = app_version
            outputBaseDir.set(project.buildDir.resolve("installers"))

            windows.apply {
                menuGroup = "Hammer"
                shortcut = true
            }
        }
        jvmArgs("-Dcompose.application.configure.swing.globals=false")
    }
}