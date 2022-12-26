package com.darkrockstudios.apps.hammer.common

import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import java.io.File
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
    return "Desktop"
}

actual fun getRootDocumentDirectory(): String = System.getProperty("user.home")

private val CONFIG_DIR = ".hammer"
actual fun getConfigDirectory(): String =
    File(File(System.getProperty("user.home")), CONFIG_DIR).absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val defaultDispatcher: CoroutineContext = Dispatchers.Default
actual val mainDispatcher: CoroutineContext = Dispatchers.Main