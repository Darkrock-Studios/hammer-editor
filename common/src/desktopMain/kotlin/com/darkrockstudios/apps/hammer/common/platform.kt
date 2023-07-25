package com.darkrockstudios.apps.hammer.common

import kotlinx.coroutines.Dispatchers
import net.harawata.appdirs.AppDirs
import net.harawata.appdirs.AppDirsFactory
import okio.FileSystem
import java.io.File
import kotlin.coroutines.CoroutineContext


var appDirs: AppDirs = AppDirsFactory.getInstance()

actual fun getPlatformName(): String {
	return "Desktop"
}

private val AUTHOR = "DarkrockStudios"

actual fun getHomeDirectory(): String = System.getProperty("user.home")

actual fun getCacheDirectory(): String = File(appDirs.getUserCacheDir(configDir(), DATA_VERSION, AUTHOR)).absolutePath

private val IMAGE_CACHE_DIR = "images"
actual fun getImageCacheDirectory(): String = File(getCacheDirectory(), IMAGE_CACHE_DIR).absolutePath

actual fun getDefaultRootDocumentDirectory(): String = System.getProperty("user.home")

private val CONFIG_DIR = "hammer"
private fun configDir(): String {
	return if (getInDevelopmentMode()) {
		"$CONFIG_DIR-dev"
	} else {
		CONFIG_DIR
	}
}

actual fun getConfigDirectory(): String =
	File(appDirs.getUserConfigDir(configDir(), CONFIG_DATA_VERSION.toString(), AUTHOR)).absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val platformDefaultDispatcher: CoroutineContext = Dispatchers.Default
actual val platformIoDispatcher: CoroutineContext = Dispatchers.IO
actual val platformMainDispatcher: CoroutineContext = Dispatchers.Main
