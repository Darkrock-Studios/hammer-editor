package de.jonasbroeckmann.kzip

import kotlinx.cinterop.*
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import platform.posix.*

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun openSourceAt(path: Path, offset: Long): RawSource {
	val file = fopen(path.toString(), "rb") ?: throw ZipException("Could not open file $path")
	if (fseek(file, offset.convert(), SEEK_SET) != 0) {
		fclose(file)
		throw ZipException("Could not seek to $offset in $path")
	}

	return object : RawSource {
		override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
			val buffer = ByteArray(byteCount.toInt().coerceAtMost(8192))
			val read = buffer.usePinned {
				fread(it.addressOf(0), 1u, buffer.size.toULong(), file)
			}.toLong()

			if (read == 0L) {
				if (feof(file) != 0) return -1L
				return 0L
			}
			sink.write(buffer, 0, read.toInt())
			return read
		}

		override fun close() {
			fclose(file)
		}
	}
}
