package com.darkrockstudios.apps.hammer.common.data.export

/** An encoded image's size in pixels, as its header states it. */
internal data class RasterSize(val widthPx: Int, val heightPx: Int)

/**
 * The size a PNG or JPEG header in [bytes] states, or null for any other format, a header cut short,
 * or a side of zero or less (a PNG side over [Int.MAX_VALUE] reads as negative). Only the header is
 * read: a corrupt body still gives a size.
 */
internal fun readRasterSize(bytes: ByteArray): RasterSize? = when {
	bytes.startsWith(PNG_SIGNATURE) -> readPngSize(bytes)
	bytes.size >= 2 && bytes.u8(0) == 0xFF && bytes.u8(1) == 0xD8 -> readJpegSize(bytes)
	else -> null
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
	size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

/** The first chunk must be IHDR, whose data opens with the width then the height, each a big-endian u32. */
private fun readPngSize(bytes: ByteArray): RasterSize? {
	if (bytes.size < 24) return null
	val width = (bytes.u16(16) shl 16) or bytes.u16(18)
	val height = (bytes.u16(20) shl 16) or bytes.u16(22)
	return sizeOrNull(width, height)
}

/**
 * Walks the segments after SOI to the first start-of-frame, whose payload opens with the precision,
 * then the height and width as big-endian u16s. Each segment is FF, a marker, then a big-endian length
 * that counts itself; FF fill bytes may come before a marker.
 */
private fun readJpegSize(bytes: ByteArray): RasterSize? {
	var offset = 2
	while (offset < bytes.size - 1) {
		if (bytes.u8(offset) != 0xFF) return null
		offset++
		while (bytes.u8(offset) == 0xFF) {
			offset++
			if (offset >= bytes.size) return null
		}
		val marker = bytes.u8(offset)
		// SOI, EOI, RST0 to RST7 and TEM stand alone, with no length or payload.
		if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
			offset++
			continue
		}
		if (offset + 3 >= bytes.size) return null
		val length = bytes.u16(offset + 1)
		if (length < 2) return null
		// C0 to CF are start-of-frame markers, except C4 (DHT), C8 (JPG) and CC (DAC).
		if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
			if (offset + 8 >= bytes.size) return null
			return sizeOrNull(width = bytes.u16(offset + 6), height = bytes.u16(offset + 4))
		}
		offset += 1 + length
	}
	return null
}

private fun sizeOrNull(width: Int, height: Int): RasterSize? =
	if (width > 0 && height > 0) RasterSize(width, height) else null
