package com.darkrockstudios.apps.hammer.common.fileio

import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val externalFileIoModule = module {
	singleOf(::DesktopExternalFileIo) bind ExternalFileIo::class
}

private class DesktopExternalFileIo(private val fileSystem: FileSystem) : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		return fileSystem.read(path.toPath()) {
			readByteArray()
		}
	}

	override fun writeExternalFile(path: String, content: String) {
		fileSystem.write(path.toPath()) {
			writeUtf8(content)
		}
	}
}