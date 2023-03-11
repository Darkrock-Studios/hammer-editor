package com.darkrockstudios.apps.hammer.utilities

import java.security.SecureRandom
import java.util.*

class RandomString(length: Int) {
    private val random: Random = SecureRandom.getInstanceStrong()
    private val buf: CharArray

    init {
        require(length >= 1) { "length < 1: $length" }
        buf = CharArray(length)
    }

    suspend fun nextString(): String {
        for (idx in buf.indices) buf[idx] = symbols[random.nextInt(symbols.length)]
        return String(buf)
    }

    companion object {
        private const val symbols = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789"
    }
}