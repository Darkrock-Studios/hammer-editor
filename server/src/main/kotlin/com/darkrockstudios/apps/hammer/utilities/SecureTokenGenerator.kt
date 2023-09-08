package com.darkrockstudios.apps.hammer.utilities

import korlibs.crypto.SecureRandom
import korlibs.crypto.encoding.Base64

class SecureTokenGenerator(private val length: Int) {
	fun generateToken(): String {
		val randomBytes = SecureRandom.nextBytes(length)
		return Base64.encode(randomBytes).substring(0, length)
	}
}