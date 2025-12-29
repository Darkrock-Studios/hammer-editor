package de.jonasbroeckmann.kzip

import kotlinx.io.RawSource
import kotlinx.io.asSource
import kotlinx.io.files.Path
import java.io.FileInputStream

internal actual fun openSourceAt(path: Path, offset: Long): RawSource {
	val fis = FileInputStream(path.toString())
	try {
		if (offset > 0) {
			fis.channel.position(offset)
		}
		return fis.asSource()
	} catch (e: Exception) {
		fis.close()
		throw e
	}
}
