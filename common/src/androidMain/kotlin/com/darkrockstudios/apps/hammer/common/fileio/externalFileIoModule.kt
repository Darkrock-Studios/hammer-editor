package com.darkrockstudios.apps.hammer.common.fileio

import android.content.Context
import androidx.core.net.toUri
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

actual val externalFileIoModule = module {
	singleOf(::AndroidExternalFileIo) bind ExternalFileIo::class
}

private class AndroidExternalFileIo(private val appContext: Context) : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		val uri = path.toUri()
		return when (uri.scheme) {
			"content" -> {
				appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
					?: error("Failed to read external file: $path")
			}

			"file" -> File(requireNotNull(uri.path) { "Missing path in file URI: $path" }).readBytes()
			else -> File(path).readBytes()
		}
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