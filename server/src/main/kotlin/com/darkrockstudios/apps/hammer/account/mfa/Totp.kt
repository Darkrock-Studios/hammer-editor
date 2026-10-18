package com.darkrockstudios.apps.hammer.account.mfa

import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP over HMAC-SHA1, the parameters every authenticator app supports. */
object Totp {
	const val DIGITS = MfaMethod.TOTP_DIGITS
	const val STEP_SECONDS = 30L
	const val SECRET_BYTES = 20
	private const val DRIFT_STEPS = 1
	private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
	private val random = SecureRandom()

	fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also(random::nextBytes)

	fun stepAt(epochSeconds: Long): Long = Math.floorDiv(epochSeconds, STEP_SECONDS)

	fun code(secret: ByteArray, step: Long, digits: Int = DIGITS): String {
		val mac = Mac.getInstance("HmacSHA1")
		mac.init(SecretKeySpec(secret, "HmacSHA1"))
		val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
		val offset = hash.last().toInt() and 0x0f
		val binary = ByteBuffer.wrap(hash, offset, Int.SIZE_BYTES).int and 0x7fffffff
		var modulus = 1
		repeat(digits) { modulus *= 10 }
		return (binary % modulus).toString().padStart(digits, '0')
	}

	/**
	 * Returns the step [code] matched within the drift window, or null. Steps at or before
	 * [lastStep] are refused so a code cannot be replayed.
	 */
	fun verify(secret: ByteArray, code: String, epochSeconds: Long, lastStep: Long?): Long? {
		val candidate = code.filterNot { it.isWhitespace() }
		if (candidate.length != DIGITS || !candidate.all { it in '0'..'9' }) return null
		val now = stepAt(epochSeconds)
		// Every step is computed so timing does not reveal which one matched.
		var matched: Long? = null
		for (step in (now - DRIFT_STEPS)..(now + DRIFT_STEPS)) {
			val equal = MessageDigest.isEqual(code(secret, step).toByteArray(), candidate.toByteArray())
			if (equal && matched == null && (lastStep == null || step > lastStep)) matched = step
		}
		return matched
	}

	fun base32Encode(data: ByteArray): String {
		val out = StringBuilder((data.size * 8 + 4) / 5)
		var buffer = 0
		var bits = 0
		for (byte in data) {
			buffer = (buffer shl 8) or (byte.toInt() and 0xff)
			bits += 8
			while (bits >= 5) {
				out.append(BASE32_ALPHABET[(buffer shr (bits - 5)) and 0x1f])
				bits -= 5
			}
		}
		if (bits > 0) out.append(BASE32_ALPHABET[(buffer shl (5 - bits)) and 0x1f])
		return out.toString()
	}

	/** Case-insensitive; ignores padding and whitespace. */
	fun base32Decode(text: String): ByteArray {
		val clean = text.uppercase().filterNot { it == '=' || it.isWhitespace() }
		val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
		var buffer = 0
		var bits = 0
		for (char in clean) {
			val value = BASE32_ALPHABET.indexOf(char)
			require(value >= 0) { "Invalid Base32 character" }
			buffer = (buffer shl 5) or value
			bits += 5
			if (bits >= 8) {
				out.write((buffer shr (bits - 8)) and 0xff)
				bits -= 8
			}
		}
		return out.toByteArray()
	}

	fun otpauthUri(secret: ByteArray, issuer: String, accountName: String): String {
		val label = "${encode(issuer)}:${encode(accountName)}"
		return "otpauth://totp/$label?secret=${base32Encode(secret)}&issuer=${encode(issuer)}" +
			"&algorithm=SHA1&digits=$DIGITS&period=$STEP_SECONDS"
	}

	private fun encode(value: String): String =
		URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
