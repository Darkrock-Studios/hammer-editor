package com.darkrockstudios.apps.hammer

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

const val DATA_DIR = "hammer_data"

fun getRootDataDirectory(fileSystem: FileSystem): Path {
    return System.getProperty("user.home").toPath() / DATA_DIR
}