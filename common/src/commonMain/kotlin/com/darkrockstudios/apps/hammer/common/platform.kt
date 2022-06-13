package com.darkrockstudios.apps.hammer.common

import okio.FileSystem
import kotlin.coroutines.CoroutineContext

expect fun getPlatformName(): String

expect fun getRootDocumentDirectory(): String

expect fun getPlatformFilesystem(): FileSystem

expect val defaultDispatcher: CoroutineContext
expect val uiDispatcher: CoroutineContext