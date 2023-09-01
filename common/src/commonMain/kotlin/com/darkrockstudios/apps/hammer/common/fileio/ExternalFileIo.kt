package com.darkrockstudios.apps.hammer.common.fileio

import org.koin.core.module.Module

interface ExternalFileIo {
	fun readExternalFile(path: String): ByteArray
	fun writeExternalFile(path: String, content: String)
}

expect val externalFileIoModule: Module