package com.darkrockstudios.apps.hammer.common.data.export

private const val MARKER_APP1 = 0xE1
private const val MARKER_SOS = 0xDA
private const val MARKER_EOI = 0xD9
private const val TAG_ORIENTATION = 0x0112
private const val TYPE_SHORT = 3
private const val IFD_ENTRY_SIZE = 12
private val EXIF_HEADER = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)

/** The EXIF orientation (1 to 8) of a JPEG; 1 when it has none or it cannot be read. */
internal fun jpegExifOrientation(bytes: ByteArray): Int {
	if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != 0xD8) return 1
	var pos = 2
	while (pos + 4 <= bytes.size) {
		if (bytes.u8(pos) != 0xFF) return 1
		val marker = bytes.u8(pos + 1)
		// A marker may be preceded by any number of 0xFF fill bytes.
		if (marker == 0xFF) {
			pos++
			continue
		}
		if (marker == MARKER_SOS || marker == MARKER_EOI) return 1
		val length = bytes.u16(pos + 2, bigEndian = true)
		val end = pos + 2 + length
		if (length < 2 || end > bytes.size) return 1
		if (marker == MARKER_APP1) tiffOrientation(bytes, start = pos + 4, end = end)?.let { return it }
		pos = end
	}
	return 1
}

/** The orientation in an APP1 segment spanning [start] to [end]; null when it is not EXIF (XMP, say). */
private fun tiffOrientation(bytes: ByteArray, start: Int, end: Int): Int? {
	if (end - start < EXIF_HEADER.size) return null
	for (i in EXIF_HEADER.indices) if (bytes[start + i] != EXIF_HEADER[i]) return null
	val tiff = start + EXIF_HEADER.size
	if (end - tiff < 8) return 1
	val bigEndian = when {
		bytes.u8(tiff) == 'M'.code && bytes.u8(tiff + 1) == 'M'.code -> true
		bytes.u8(tiff) == 'I'.code && bytes.u8(tiff + 1) == 'I'.code -> false
		else -> return 1
	}
	val ifdOffset = bytes.u32(tiff + 4, bigEndian)
	if (ifdOffset > end - tiff - 2) return 1
	val ifd = tiff + ifdOffset.toInt()
	val count = bytes.u16(ifd, bigEndian)
	for (i in 0 until count) {
		val entry = ifd + 2 + i * IFD_ENTRY_SIZE
		if (entry + IFD_ENTRY_SIZE > end) return 1
		if (bytes.u16(entry, bigEndian) != TAG_ORIENTATION) continue
		if (bytes.u16(entry + 2, bigEndian) != TYPE_SHORT) return 1
		return bytes.u16(entry + 8, bigEndian).takeIf { it in 1..8 } ?: 1
	}
	return 1
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u16(index: Int, bigEndian: Boolean): Int =
	if (bigEndian) (u8(index) shl 8) or u8(index + 1) else (u8(index + 1) shl 8) or u8(index)

private fun ByteArray.u32(index: Int, bigEndian: Boolean): Long {
	val high = u16(if (bigEndian) index else index + 2, bigEndian).toLong()
	val low = u16(if (bigEndian) index + 2 else index, bigEndian).toLong()
	return (high shl 16) or low
}
