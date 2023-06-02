package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import org.koin.core.context.startKoin
import platform.Foundation.*
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
	return "iOS"
}

actual fun getHomeDirectory(): String {
	val urls = NSFileManager.defaultManager.URLsForDirectory(directory = NSUserDirectory, inDomains = NSUserDomainMask)
	return (urls[0] as NSURL).path!!
}

actual fun getCacheDirectory(): String {
	val urls =
		NSFileManager.defaultManager.URLsForDirectory(directory = NSCachesDirectory, inDomains = NSUserDomainMask)
	return (urls[0] as NSURL).path!!
}

actual fun getImageCacheDirectory(): String {
	val urls =
		NSFileManager.defaultManager.URLsForDirectory(directory = NSCachesDirectory, inDomains = NSUserDomainMask)
	return (urls[0] as NSURL).path!!
}

actual fun getDefaultRootDocumentDirectory(): String {
	val urls =
		NSFileManager.defaultManager.URLsForDirectory(directory = NSDocumentDirectory, inDomains = NSUserDomainMask)
	return (urls[0] as NSURL).path!!
}

actual fun getConfigDirectory(): String {
	val urls =
		NSFileManager.defaultManager.URLsForDirectory(directory = NSDocumentDirectory, inDomains = NSUserDomainMask)
	return (urls[0] as NSURL).path!!
}

fun initializeKoin() {
	startKoin {
		logger(NapierLogger())
		modules(mainModule)
	}
}

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val platformDefaultDispatcher: CoroutineContext = Dispatchers.Default
actual val platformIoDispatcher: CoroutineContext = Dispatchers.Default
actual val platformMainDispatcher: CoroutineContext = Dispatchers.Main