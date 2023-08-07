package com.darkrockstudios.apps.hammer.common

import okio.FileSystem
import kotlin.coroutines.CoroutineContext

expect fun getHomeDirectory(): String
expect fun getCacheDirectory(): String
expect fun getImageCacheDirectory(): String
expect fun getPlatformName(): String
expect fun getDefaultRootDocumentDirectory(): String
expect fun getConfigDirectory(): String
expect fun getPlatformFilesystem(): FileSystem

/**
 * Do not reference this directly, use injected
 */
internal expect val platformDefaultDispatcher: CoroutineContext

/**
 * Do not reference this directly, use injected
 */
expect val platformIoDispatcher: CoroutineContext

/**
 * Do not reference this directly, use injected
 */
expect val platformMainDispatcher: CoroutineContext