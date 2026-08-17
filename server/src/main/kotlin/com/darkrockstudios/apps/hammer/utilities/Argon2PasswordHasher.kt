package com.darkrockstudios.apps.hammer.utilities

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2 password hashing that reads and writes the standard PHC encoded string:
 * `$argon2i$v=19$m=65536,t=3,p=2$<b64 salt>$<b64 hash>`.
 *
 * [verify] takes every parameter from the stored string, so hashes written with any
 * variant, version, or cost settings keep working.
 *
 * Pure JVM on purpose: a libargon2 binding extracts a shared object at runtime and
 * executes it, which fails outright wherever the extraction directory is mounted
 * noexec, as it commonly is on hardened container hosts.
 */
object Argon2PasswordHasher {

	const val SALT_LENGTH = 16
	const val HASH_LENGTH = 32

	private const val ARGON2_I = "argon2i"
	private const val ARGON2_D = "argon2d"
	private const val ARGON2_ID = "argon2id"

	private const val MAX_MEMORY_KIB = 1024 * 1024
	private const val MAX_ITERATIONS = 100
	private const val MAX_PARALLELISM = 64

	// Absent `v=` means the original Argon2 version, which predates the marker.
	private val DEFAULT_VERSION = Argon2Parameters.ARGON2_VERSION_10

	private val secureRandom = SecureRandom()
	private val encoder = Base64.getEncoder().withoutPadding()
	private val decoder = Base64.getDecoder()

	fun hash(password: CharArray, memoryKib: Int, iterations: Int, parallelism: Int): String {
		val salt = ByteArray(SALT_LENGTH)
		secureRandom.nextBytes(salt)

		val hash = derive(
			type = Argon2Parameters.ARGON2_i,
			version = Argon2Parameters.ARGON2_VERSION_13,
			salt = salt,
			memoryKib = memoryKib,
			iterations = iterations,
			parallelism = parallelism,
			password = password,
			outputLength = HASH_LENGTH,
		)

		return buildString {
			append('$').append(ARGON2_I)
			append("\$v=").append(Argon2Parameters.ARGON2_VERSION_13)
			append("\$m=").append(memoryKib)
			append(",t=").append(iterations)
			append(",p=").append(parallelism)
			append('$').append(encoder.encodeToString(salt))
			append('$').append(encoder.encodeToString(hash))
		}
	}

	/** Returns false for anything that isn't a well-formed hash of [password]. */
	fun verify(encoded: String, password: CharArray): Boolean {
		val parsed = parse(encoded) ?: return false

		val computed = derive(
			type = parsed.type,
			version = parsed.version,
			salt = parsed.salt,
			memoryKib = parsed.memoryKib,
			iterations = parsed.iterations,
			parallelism = parsed.parallelism,
			password = password,
			outputLength = parsed.hash.size,
		)

		return MessageDigest.isEqual(parsed.hash, computed)
	}

	@Suppress("LongParameterList")
	private fun derive(
		type: Int,
		version: Int,
		salt: ByteArray,
		memoryKib: Int,
		iterations: Int,
		parallelism: Int,
		password: CharArray,
		outputLength: Int,
	): ByteArray {
		val parameters = Argon2Parameters.Builder(type)
			.withVersion(version)
			.withSalt(salt)
			.withMemoryAsKB(memoryKib)
			.withIterations(iterations)
			.withParallelism(parallelism)
			.build()

		val generator = Argon2BytesGenerator()
		generator.init(parameters)

		val output = ByteArray(outputLength)
		generator.generateBytes(password, output)
		return output
	}

	private class Parsed(
		val type: Int,
		val version: Int,
		val memoryKib: Int,
		val iterations: Int,
		val parallelism: Int,
		val salt: ByteArray,
		val hash: ByteArray,
	)

	@Suppress("ReturnCount", "MagicNumber")
	private fun parse(encoded: String): Parsed? {
		// The leading '$' makes the first field empty, so a full hash splits into six.
		val fields = encoded.split('$')
		if (fields.size !in 5..6 || fields[0].isNotEmpty()) return null

		val type = when (fields[1]) {
			ARGON2_I -> Argon2Parameters.ARGON2_i
			ARGON2_D -> Argon2Parameters.ARGON2_d
			ARGON2_ID -> Argon2Parameters.ARGON2_id
			else -> return null
		}

		val hasVersion = fields.size == 6
		val version = if (hasVersion) {
			val field = fields[2]
			if (!field.startsWith("v=")) return null
			field.removePrefix("v=").toIntOrNull() ?: return null
		} else {
			DEFAULT_VERSION
		}
		if (version != Argon2Parameters.ARGON2_VERSION_10 && version != Argon2Parameters.ARGON2_VERSION_13) return null

		val costs = fields[if (hasVersion) 3 else 2]
			.split(',')
			.map { it.split('=', limit = 2) }
		if (costs.any { it.size != 2 }) return null
		val costsByKey = costs.associate { it[0] to it[1] }

		// keyid/data would change the derivation and we never write them.
		if (costsByKey.keys != setOf("m", "t", "p")) return null

		val memoryKib = costsByKey.getValue("m").toIntOrNull() ?: return null
		val iterations = costsByKey.getValue("t").toIntOrNull() ?: return null
		val parallelism = costsByKey.getValue("p").toIntOrNull() ?: return null

		// The lower bounds are what the algorithm requires. The upper bounds keep a
		// corrupt stored value from allocating the heap away or hashing for hours.
		if (parallelism < 1 || parallelism > MAX_PARALLELISM) return null
		if (iterations < 1 || iterations > MAX_ITERATIONS) return null
		if (memoryKib < 8 * parallelism || memoryKib > MAX_MEMORY_KIB) return null

		val salt = decodeOrNull(fields[if (hasVersion) 4 else 3]) ?: return null
		val hash = decodeOrNull(fields[if (hasVersion) 5 else 4]) ?: return null
		if (salt.isEmpty() || hash.isEmpty()) return null

		return Parsed(type, version, memoryKib, iterations, parallelism, salt, hash)
	}

	@Suppress("SwallowedException")
	private fun decodeOrNull(value: String): ByteArray? = try {
		decoder.decode(value)
	} catch (_: IllegalArgumentException) {
		null
	}
}
