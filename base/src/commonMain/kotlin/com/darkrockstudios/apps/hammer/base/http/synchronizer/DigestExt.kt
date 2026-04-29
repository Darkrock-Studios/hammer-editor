package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Digest

internal fun Digest<*>.update(string: String, buf: ByteArray = ByteArray(4)) {
	for (char in string) {
		update(char.code, buf)
	}
}

internal fun Digest<*>.update(data: Int, buffer: ByteArray = ByteArray(4)) {
	buffer[0] = (data shr 0).toByte()
	buffer[1] = (data shr 8).toByte()
	buffer[2] = (data shr 16).toByte()
	buffer[3] = (data shr 24).toByte()

	update(buffer)
}

internal fun Digest<*>.update(data: Long, buffer: ByteArray = ByteArray(4)) {
	buffer[0] = (data shr 0).toByte()
	buffer[1] = (data shr 8).toByte()
	buffer[2] = (data shr 16).toByte()
	buffer[3] = (data shr 24).toByte()
	update(buffer)

	buffer[0] = (data shr 32).toByte()
	buffer[1] = (data shr 40).toByte()
	buffer[2] = (data shr 48).toByte()
	buffer[3] = (data shr 56).toByte()
	update(buffer)
}
