// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// F-Droid's buildserver cannot reach the foojay disco API to resolve toolchains,
// and cannot provide a JetBrains Runtime — both of which the :desktop module
// requires. F-Droid only ships the Android app, so when -Pfdroid=true is set
// we drop foojay and exclude :desktop entirely.
//
// The plugins {} block is compiled in a restricted scope and can't see vars
// defined outside it, so the condition is inlined.
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
