package com.darkrockstudios.apps.hammer.utilities

import java.security.MessageDigest

/** Lowercase hex SHA-256 of [bytes]. */
fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** Lowercase hex SHA-256 of [value]'s UTF-8 encoding. */
fun sha256Hex(value: String): String = sha256Hex(value.toByteArray(Charsets.UTF_8))

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
