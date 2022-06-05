package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.data.Project
import platform.Foundation.NSLog

actual fun getPlatformName(): String {
    return "iOS"
}

actual fun getProjectsForDirectory(path: String): List<Project> {
    NSLog("Test")
    return listOf(
        Project(name = "Proj 1", path="/a/b/c"),
        Project(name = "Proj 2", path="/a/b/c"),
        Project(name = "Proj 3", path="/a/b/c")
    )
}