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

actual fun getDefaultRootDocumentDirectory(): String = getDocumentsDirectory().absolutePath

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

actual fun getLogDirectory(): String? = File(getConfigDirectory(), "logs").absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val platformDefaultDispatcher: CoroutineContext = Dispatchers.Default
actual val platformIoDispatcher: CoroutineContext = Dispatchers.IO
actual val platformMainDispatcher: CoroutineContext = Dispatchers.Main

/**
 * Gets the user's Documents directory, respecting sandboxing on all platforms
 */
private fun getDocumentsDirectory(): File {
	return when (val os = System.getProperty("os.name").lowercase()) {
		"linux", "freebsd", "openbsd", "netbsd" -> getLinuxDocuments()
		else -> when {
			os.startsWith("mac") || os.startsWith("darwin") -> getMacosDocuments()
			os.startsWith("windows") -> getWindowsDocuments()
			// Unknown OS: Safe fallback
			else -> File(System.getProperty("user.home"), "Documents")
		}
	}
}

private fun getMacosDocuments(): File {
	// macOS: Always ~/Documents (sandbox-aware)
	return File(System.getProperty("user.home"), "Documents")
}

private fun getLinuxDocuments(): File {
	// Linux/BSD: Check XDG_DOCUMENTS_DIR first (Flatpak sets this)
	val xdgDocs = System.getenv("XDG_DOCUMENTS_DIR")
	return if (xdgDocs != null) {
		File(xdgDocs)
	} else {
		File(System.getProperty("user.home"), "Documents")
	}
}

private fun getWindowsDocuments(): File {
	// Windows: Use shell folder or fallback
	try {
		val shellFolder = Class.forName("sun.awt.shell.ShellFolder")
		val getMethod = shellFolder.getMethod("get", String::class.java)
		val folder = getMethod.invoke(null, "personal") as File
		return folder
	} catch (_: Exception) {
		val userProfile = System.getenv("USERPROFILE")
		return File(userProfile, "Documents")
	}
}
