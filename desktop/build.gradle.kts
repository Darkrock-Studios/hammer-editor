import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val app_version: String by extra

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

group = "com.darkrockstudios.apps.hammer.desktop"
version = app_version


kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
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
                implementation("com.github.Dansoftowner:jSystemThemeDetector:3.8")
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
            packageName = "hammer"
            packageVersion = app_version
        }
        jvmArgs("-Dcompose.application.configure.swing.globals=false")
    }
}
