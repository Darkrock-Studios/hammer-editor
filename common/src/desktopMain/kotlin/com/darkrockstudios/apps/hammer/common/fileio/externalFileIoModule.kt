package com.darkrockstudios.apps.hammer.common.fileio

import io.github.aakira.napier.Napier
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

	override fun writeExternalFile(path: String, content: ByteArray): Boolean {
		return try {
			fileSystem.write(path.toPath()) {
				write(content)
			}
			true
			// Writing an external file can fail many ways (IO, permissions); report and return false.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to write external file: $path", e)
			false
		}
	}
}