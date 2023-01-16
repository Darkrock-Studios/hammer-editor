package com.darkrockstudios.apps.hammer.common.fileio

import android.content.Context
import android.net.Uri
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val externalFileIoModule = module {
	singleOf(::AndroidExternalFileIo) bind ExternalFileIo::class
}

private class AndroidExternalFileIo(private val appContext: Context) : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		val uri = Uri.parse(path)
		var bytes: ByteArray? = null
		appContext.contentResolver.openInputStream(uri)?.use { input ->
			bytes = input.readBytes()
		}

		bytes?.let {
			return it
		} ?: error("Failed to read external file: $path")
	}
}