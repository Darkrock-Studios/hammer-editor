package com.darkrockstudios.apps.hammer.common.util

import okio.BufferedSource

private const val REPLACEMENT = '�'

/**
 * Supplies the scratch a text scan reads into. Lets a reader fill buffers the caller owns and
 * reuses, instead of handing back a freshly allocated string for every file.
 */
interface ScanBuffers {
	/** A char buffer of at least [minCapacity]. May be larger, and may be the one from last time. */
	fun charBuffer(minCapacity: Int): CharArray

	/** A byte buffer of at least [minCapacity]. May be larger, and may be the one from last time. */
	fun byteBuffer(minCapacity: Int): ByteArray
}

/**
 * Reads up to [byteCount] bytes, decodes them as UTF-8 into [buffers]' char buffer, and returns how
 * many chars landed there.
 *
 * The bytes are pulled in bulk and decoded from an array rather than a byte at a time: reading one
 * byte at a time off a source costs more than the decode itself.
 */
fun BufferedSource.readUtf8Into(buffers: ScanBuffers, byteCount: Int): Int {
	if (byteCount <= 0) return 0
	val bytes = buffers.byteBuffer(byteCount)
	var read = 0
	while (read < byteCount) {
		val n = read(bytes, read, byteCount - read)
		if (n == -1) break
		read += n
	}
	// UTF-8 never decodes to more UTF-16 chars than it has bytes, so this always fits.
	return decodeUtf8(bytes, read, buffers.charBuffer(read))
}

/**
 * Decodes the first [length] bytes of [bytes] as UTF-8 into [sink], returning the char count.
 * Malformed input decodes to U+FFFD rather than throwing, matching how the rest of the app treats a
 * damaged file: degraded, not fatal.
 */
internal fun decodeUtf8(bytes: ByteArray, length: Int, sink: CharArray): Int {
	var i = 0
	var out = 0
	while (i < length && out < sink.size) {
		val b0 = bytes[i].toInt() and 0xFF
		i++

		if (b0 < 0x80) {
			sink[out++] = b0.toChar()
			continue
		}

		val needed = when {
			b0 and 0xE0 == 0xC0 -> 1
			b0 and 0xF0 == 0xE0 -> 2
			b0 and 0xF8 == 0xF0 -> 3
			else -> -1
		}
		if (needed < 0 || i + needed > length) {
			sink[out++] = REPLACEMENT
			continue
		}

		var codePoint = when (needed) {
			1 -> b0 and 0x1F
			2 -> b0 and 0x0F
			else -> b0 and 0x07
		}
		var malformed = false
		for (k in 0 until needed) {
			val b = bytes[i + k].toInt() and 0xFF
			if (b and 0xC0 != 0x80) {
				malformed = true
				break
			}
			codePoint = (codePoint shl 6) or (b and 0x3F)
		}
		if (malformed) {
			sink[out++] = REPLACEMENT
			continue
		}
		i += needed

		if (codePoint > 0x10FFFF) {
			sink[out++] = REPLACEMENT
		} else if (codePoint < 0x10000) {
			sink[out++] = codePoint.toChar()
		} else {
			// Astral planes need a surrogate pair, the one case where a sequence yields two chars.
			if (out + 1 >= sink.size) break
			val v = codePoint - 0x10000
			sink[out++] = (0xD800 + (v shr 10)).toChar()
			sink[out++] = (0xDC00 + (v and 0x3FF)).toChar()
		}
	}
	return out
}
