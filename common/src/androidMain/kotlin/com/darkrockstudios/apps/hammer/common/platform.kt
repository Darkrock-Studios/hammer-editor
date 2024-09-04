package com.darkrockstudios.apps.hammer.common

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import java.io.File
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
	return "Android"
}

actual fun getHomeDirectory(): String = getDefaultRootDocumentDirectory()

private lateinit var rootDocumentDirectory: File
private lateinit var configDirectory: File
fun setInternalDirectories(context: Context) {
	rootDocumentDirectory = context.filesDir
	configDirectory = context.filesDir
	cacheDirectory = context.cacheDir
}

fun setExternalDirectories(context: Context) {
	val path = Environment.getExternalStoragePublicDirectory(
		Environment.DIRECTORY_DOCUMENTS
	)
	rootDocumentDirectory = path
	configDirectory = context.filesDir
	cacheDirectory = context.cacheDir
}

private lateinit var cacheDirectory: File
actual fun getCacheDirectory(): String {
	return cacheDirectory.absolutePath
}

actual fun getImageCacheDirectory(): String {
	return File(getCacheDirectory(), "images").absolutePath
}

actual fun getDefaultRootDocumentDirectory(): String = rootDocumentDirectory.absolutePath
actual fun getConfigDirectory(): String = File(configDirectory, "config").absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val platformDefaultDispatcher: CoroutineContext = Dispatchers.Default
actual val platformIoDispatcher: CoroutineContext = Dispatchers.IO
actual val platformMainDispatcher: CoroutineContext = Dispatchers.Main