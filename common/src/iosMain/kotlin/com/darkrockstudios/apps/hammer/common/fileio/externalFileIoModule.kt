package com.darkrockstudios.apps.hammer.common.fileio

import com.darkrockstudios.apps.hammer.common.util.toByteArray
import com.darkrockstudios.apps.hammer.common.util.toNSData
import io.github.aakira.napier.Napier
import kotlinx.cinterop.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.*

actual val externalFileIoModule = module {
	singleOf(::IosExternalFileIo) bind ExternalFileIo::class
}

private class IosExternalFileIo : ExternalFileIo {
	override fun readExternalFile(path: String): ByteArray {
		val url = if (path.startsWith("file://")) {
			NSURL.URLWithString(path)
		} else {
			NSURL.fileURLWithPath(path)
		} ?: error("Failed to parse external file path: $path")
		val data = NSData.dataWithContentsOfURL(url)
			?: error("Failed to read external file: $path")
		return data.toByteArray()
	}

	@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
	override fun writeExternalFile(path: String, content: ByteArray): Boolean = memScoped {
		val data = content.toNSData()
		val url = if (path.startsWith("file://")) {
			NSURL.URLWithString(path)
		} else {
			NSURL.fileURLWithPath(path)
		} ?: run {
			Napier.e("Failed to parse external file path: $path")
			return@memScoped false
		}
		val errorVar = alloc<ObjCObjectVar<NSError?>>()
		val ok = data.writeToURL(url, options = NSDataWritingAtomic, error = errorVar.ptr)
		if (!ok) {
			Napier.e("Failed to write external file $path: ${errorVar.value?.localizedDescription}")
		}
		ok
	}
}
