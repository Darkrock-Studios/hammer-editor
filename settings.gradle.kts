// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// F-Droid can't provide JBR or reach foojay, so skip both when -Pfdroid=true.
plugins {
    if (startParameter.projectProperties["fdroid"]?.isNotEmpty() != true &&
        System.getenv("FDROID_BUILD") == null
    ) {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

rootProject.name = "hammer"

val isFDroidBuild = startParameter.projectProperties["fdroid"]?.isNotEmpty() == true ||
    System.getenv("FDROID_BUILD") != null

val modules = mutableListOf(":base", ":android", ":composeUi", ":common", ":server", ":integrationTests")
if (!isFDroidBuild) {
    modules += ":desktop"
}
include(*modules.toTypedArray())
