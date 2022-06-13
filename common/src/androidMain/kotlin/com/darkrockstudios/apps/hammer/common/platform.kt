package com.darkrockstudios.apps.hammer.common

import android.content.Context
import okio.FileSystem
import java.io.File

actual fun getPlatformName(): String {
    return "Android"
}

private lateinit var rootDocumentDirectory: File
fun setRootDocumentDirectory(context: Context) {
    rootDocumentDirectory = context.filesDir
}

actual fun getRootDocumentDirectory(): String = rootDocumentDirectory.absolutePath

actual fun getPlatformFilesystem() = FileSystem.SYSTEM