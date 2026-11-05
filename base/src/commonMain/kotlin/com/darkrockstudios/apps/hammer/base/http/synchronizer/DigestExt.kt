package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Digest

internal fun Digest<*>.update(string: String, buf: ByteArray = ByteArray(4)) {
	for (char in string) {
		update(char.code, buf)
	}
}

/** Length-prefixed, so adjacent strings cannot bleed into each other ("ab"+"c" vs "a"+"bc"). */
internal fun Digest<*>.updateSized(value: String, buf: ByteArray = ByteArray(4)) {
	update(value.length, buf)
	update(value, buf)
}

/** A `0`/`1` presence marker, then the length-prefixed value when present. */
internal fun Digest<*>.updateOptionalSized(value: String?, buf: ByteArray = ByteArray(4)) {
	if (value != null) {
		update(1, buf)
		updateSized(value, buf)
	} else {
		update(0, buf)
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
