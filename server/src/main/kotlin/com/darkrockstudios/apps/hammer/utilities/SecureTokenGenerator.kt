package com.darkrockstudios.apps.hammer.utilities

import com.soywiz.krypto.SecureRandom
import com.soywiz.krypto.encoding.Base64

class SecureTokenGenerator(private val length: Int) {
    fun generateToken(): String {
        val randomBytes = SecureRandom.nextBytes(length)
        return Base64.encode(randomBytes).substring(0, length)
    }
}