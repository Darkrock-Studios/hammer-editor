package com.darkrockstudios.apps.hammer.common.fileio

import org.koin.core.module.Module

interface ExternalFileIo {
	fun readExternalFile(path: String): ByteArray
}

expect val externalFileIoModule: Module