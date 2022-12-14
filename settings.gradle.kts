// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        kotlin("multiplatform").version(extra["kotlin_version"] as String)
        kotlin("plugin.serialization").version(extra["kotlin_version"] as String)
        kotlin("android").version(extra["kotlin_version"] as String)
        id("com.android.application").version(extra["agp_version"] as String)
        id("com.android.library").version(extra["agp_version"] as String)
        id("org.jetbrains.compose").version(extra["jetbrains_compose_version"] as String)
    }
}

rootProject.name = "hammer"

include(":android", ":desktop", ":composeUi", ":common")
