package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import korlibs.crypto.encoding.Base64

/** Smallest byte sequences that pass `ImageFormat.sniff`, so the client and server accept them. */
object TestImages {
	fun png(marker: Int = 0): ByteArray =
		byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, marker.toByte())

	fun jpeg(marker: Int = 0): ByteArray =
		byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), marker.toByte())

	fun svg(): ByteArray = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()

	fun entity(id: Int, name: String, fileExtension: String, bytes: ByteArray) = ApiProjectEntity.ImageEntity(
		id = id,
		name = name,
		fileExtension = fileExtension,
		base64 = Base64.encode(bytes, url = true),
	)
}
