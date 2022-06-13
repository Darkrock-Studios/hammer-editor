package com.darkrockstudios.apps.hammer.common

import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
    return "Desktop"
}

actual fun getRootDocumentDirectory(): String = System.getProperty("user.home")

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val defaultDispatcher: CoroutineContext = Dispatchers.Default
actual val uiDispatcher: CoroutineContext = Dispatchers.Main