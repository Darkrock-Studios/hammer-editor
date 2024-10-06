package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class Token(
	val userId: Long,
	val auth: String,
	val refresh: String
) {
	fun isValid(): Boolean {
		return validateToken(auth) && validateToken(refresh)
	}

	@OptIn(ExperimentalEncodingApi::class)
	private fun validateToken(base64Encoded: String): Boolean {
		return try {
			val decoded = Base64
				.UrlSafe
				.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
				.decode(base64Encoded)
			decoded.size == LENGTH && decoded.all { it.toInt() in -128..128 }
		} catch (e: IllegalArgumentException) {
			false
		}
	}

	companion object {
		const val LENGTH = 16
	}
}