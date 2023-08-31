package com.darkrockstudios.apps.hammer.utilities

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.io.FileInputStream


const val DATA_DIR = "hammer_data"

fun getRootDataDirectory(fileSystem: FileSystem): Path {
	return System.getProperty("user.home").toPath() / DATA_DIR
}

fun File.readUtf8(bufferSize: Int = 1024): String {
	val fis = FileInputStream(this)
	var buffer = ByteArray(bufferSize)
	val sb = StringBuilder()
	while (fis.read(buffer) != -1) {
		sb.append(String(buffer))
		buffer = ByteArray(bufferSize)
	}
	fis.close()

	return sb.toString()
}