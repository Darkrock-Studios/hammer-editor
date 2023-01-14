package com.darkrockstudios.apps.hammer.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import java.io.File
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
	return "Android"
}

actual fun getHomeDirectory(): String = getRootDocumentDirectory()

private lateinit var rootDocumentDirectory: File
fun setRootDocumentDirectory(context: Context) {
	rootDocumentDirectory = context.filesDir
}

actual fun getRootDocumentDirectory(): String = rootDocumentDirectory.absolutePath
actual fun getConfigDirectory(): String = File(rootDocumentDirectory, "config").absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val defaultDispatcher: CoroutineContext = Dispatchers.Default
actual val mainDispatcher: CoroutineContext = Dispatchers.Main