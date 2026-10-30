package utils

/** Smallest byte sequences that pass ImageFormat.sniff for each format. */
object TestImages {
	fun png(marker: Int = 0): ByteArray =
		byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, marker.toByte())

	fun jpeg(marker: Int = 0): ByteArray =
		byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), marker.toByte())

	fun webp(marker: Int = 0): ByteArray =
		"RIFF".encodeToByteArray() + ByteArray(4) + "WEBP".encodeToByteArray() + marker.toByte()

	fun svg(): ByteArray = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()
}
