package com.darkrockstudios.apps.hammer.common

import okio.FileSystem

actual fun getPlatformName(): String {
    return "Desktop"
}

actual fun getRootDocumentDirectory(): String = System.getProperty("user.home")

actual fun getPlatformFilesystem() = FileSystem.SYSTEM