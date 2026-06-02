package com.darkrockstudios.apps.hammer.common.fileio

import android.content.Context
import androidx.core.net.toUri
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

actual val externalFileIoModule = module {
	singleOf(::AndroidExternalFileIo) bind ExternalFileIo::class
}

private class AndroidExternalFileIo(private val appContext: Context) : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		val uri = path.toUri()
		var bytes: ByteArray? = null
		appContext.contentResolver.openInputStream(uri)?.use { input ->
			bytes = input.readBytes()
		}

		bytes?.let {
			return it
		} ?: error("Failed to read external file: $path")
	}

	override fun writeExternalFile(path: String, content: ByteArray): Boolean {
		val contentResolver = appContext.contentResolver
		return try {
			val uri = path.toUri()
			val pfd = contentResolver.openFileDescriptor(uri, "w") ?: return false
			pfd.use {
				FileOutputStream(it.fileDescriptor).use { fos ->
					fos.write(content)
				}
			}
			true
		} catch (e: FileNotFoundException) {
			e.printStackTrace()
			false
		} catch (e: IOException) {
			e.printStackTrace()
			false
		}
	}
}