package com.darkrockstudios.apps.hammer.common

import okio.FileSystem

expect fun getPlatformName(): String

expect fun getRootDocumentDirectory(): String

expect fun getPlatformFilesystem(): FileSystem