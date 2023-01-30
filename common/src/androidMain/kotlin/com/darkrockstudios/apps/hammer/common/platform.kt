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
fun setDirectories(context: Context) {
	rootDocumentDirectory = context.filesDir
	cacheDirectory = context.cacheDir
}

private lateinit var cacheDirectory: File
actual fun getCacheDirectory(): String {
	return cacheDirectory.absolutePath
}

actual fun getImageCacheDirectory(): String {
	return File(getCacheDirectory(), "images").absolutePath
}

actual fun getRootDocumentDirectory(): String = rootDocumentDirectory.absolutePath
actual fun getConfigDirectory(): String = File(rootDocumentDirectory, "config").absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val defaultDispatcher: CoroutineContext = Dispatchers.Default
actual val ioDispatcher: CoroutineContext = Dispatchers.IO
actual val mainDispatcher: CoroutineContext = Dispatchers.Main