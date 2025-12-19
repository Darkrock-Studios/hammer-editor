package com.darkrockstudios.apps.hammer.utilities

import korlibs.crypto.SecureRandom
import kotlin.io.encoding.Base64

class SecureTokenGenerator(private val byteLength: Int, private val b64: Base64) {
	fun generateToken(): String {
		val randomBytes = SecureRandom.nextBytes(byteLength)
		return b64.encode(randomBytes)
	}
}