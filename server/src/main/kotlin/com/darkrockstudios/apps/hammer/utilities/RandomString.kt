package com.darkrockstudios.apps.hammer.utilities

import java.security.SecureRandom

class RandomString(
	private val length: Int,
	private val random: SecureRandom
) {

	init {
		require(length >= 1) { "length < 1: $length" }
	}

	/**
	 * The buffer is per-call on purpose. One shared instance serves every request, so a
	 * field-level buffer lets concurrent callers interleave writes and read back each
	 * other's characters, handing two sessions the same sync id.
	 */
	suspend fun nextString(): String {
		val buf = CharArray(length)
		for (idx in buf.indices) buf[idx] = symbols[random.nextInt(symbols.length)]
		return String(buf)
	}

	companion object {
		private const val symbols = "abcdefghijklmnopqrstuvwxyz-_ABCDEFGJKLMNPRSTUVWXYZ0123456789"
	}
}