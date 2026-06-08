// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// F-Droid can't provide JBR or reach foojay, so skip both for F-Droid builds.
// The flag is honoured from -Pfdroid=true, an `fdroid` entry in gradle.properties,
// or the FDROID_BUILD environment variable - the same sources the module build
// scripts read, so a single gradle.properties line drives the whole build.
plugins {
    val isFDroid = providers.gradleProperty("fdroid").orNull?.isNotEmpty() == true ||
        System.getenv("FDROID_BUILD") != null
    if (!isFDroid) {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

rootProject.name = "hammer"

val isFDroidBuild = providers.gradleProperty("fdroid").orNull?.isNotEmpty() == true ||
    System.getenv("FDROID_BUILD") != null

val modules = mutableListOf(":base", ":android", ":composeUi", ":common", ":server", ":integrationTests")
if (!isFDroidBuild) {
    modules += ":desktop"
}
include(*modules.toTypedArray())
