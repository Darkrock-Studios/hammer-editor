// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        kotlin("android").version(extra["kotlin_version"] as String) apply false
        id("com.android.application").version(extra["agp_version"] as String) apply false
        id("com.android.library").version(extra["agp_version"] as String) apply false
        id("org.jetbrains.compose").version(extra["jetbrains_compose_version"] as String) apply false
        id("dev.icerock.mobile.multiplatform-resources").version(extra["moko_resources_version"] as String) apply false
        id("io.ktor.plugin") version extra["ktor_version"] as String apply false
        id("app.cash.sqldelight") version extra["sqldelight_version"] as String apply false
	}
}

rootProject.name = "hammer"

include(":base", ":android", ":desktop", ":composeUi", ":common", ":server")
