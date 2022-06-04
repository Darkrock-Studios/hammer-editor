package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.data.Project

actual fun getPlatformName(): String {
    return "iOS"
}

actual fun getProjectsForDirectory(path: String): List<Project> {
    return emptyList()
}