package com.darkrockstudios.apps.hammer.common.fileio

import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val externalFileIoModule = module {
	singleOf(::IosExternalFileIo) bind ExternalFileIo::class
}

private class IosExternalFileIo(private val fileSystem: FileSystem) : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		// TODO This is just the desktop implementation, probably won't work
		return fileSystem.read(path.toPath()) {
			readByteArray()
		}
	}

	override fun writeExternalFile(path: String, content: String) {
		// TODO This is just the desktop implementation, probably won't work
		fileSystem.write(path.toPath(), false) {
			writeUtf8(content)
		}
	}
}