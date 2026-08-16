package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The reference-hash fixtures below come from the Argon2 C implementation (libargon2,
 * via de.mkammerer:argon2-jvm 2.12), which wrote every password hash on deployed
 * servers. They must keep verifying.
 */
class Argon2PasswordHasherTest {

	private val referenceHashes = listOf(
		// argon2i at the parameters AccountsRepository hashes with.
		"correct horse battery staple" to
			"\$argon2i\$v=19\$m=65536,t=3,p=2\$k7m7gU9uModw2lSbVPJ8lQ\$iBwaiktAxMo5yNDbAyxMti4I2wtZjYgsL+jB2RCcKAw",
		// A different variant and cost settings, all read back out of the string.
		"correct horse battery staple" to
			"\$argon2id\$v=19\$m=19456,t=2,p=1\$tlovharpYfIUfTm1m0FOpQ\$s0nGX03Qf8gy426+jDwY0p+kaXmjyiaGpAlXMPcjxTY",
		"a" to
			"\$argon2d\$v=19\$m=8,t=1,p=1\$FciDFN4R17rjWQgcpzuBLQ\$5sFXlzqDGdvVE3PLHftVmbgIPeD0+ROLXDoD6Ug2fk0",
		// A non-ASCII password, which only verifies if both sides encode it as UTF-8.
		"caf\u00e9 na\u00efve \u00dfeta" to
			"\$argon2i\$v=19\$m=32768,t=2,p=4\$K4OYE6n5R01UtQGT3lgcKA\$al0ciei4LlOHcylR1ZZsYmNXBHDlZ6JJJMSdSZ3f+ec",
	)

	@Test
	fun `verifies hashes written by the reference implementation`() {
		referenceHashes.forEach { (password, hash) ->
			assertTrue(
				Argon2PasswordHasher.verify(hash, password.toCharArray()),
				"Reference hash $hash must verify",
			)
		}
	}

	@Test
	fun `rejects a wrong password against a reference hash`() {
		referenceHashes.forEach { (_, hash) ->
			assertFalse(
				Argon2PasswordHasher.verify(hash, "not the password".toCharArray()),
				"Reference hash $hash must not verify against another password",
			)
		}
	}

	@Test
	fun `verifies a hash carrying no version marker`() {
		val hash = "\$argon2i\$m=1024,t=2,p=1\$c2l4dGVlbmJ5dGVzYWx0IQ\$l/Rt3Va9SLakmdDaJnB+MRNtwcxBR8XKsJpNKSEGnro"

		assertTrue(Argon2PasswordHasher.verify(hash, "legacy-format".toCharArray()))
		assertFalse(Argon2PasswordHasher.verify(hash, "legacy-formaT".toCharArray()))
	}

	@Test
	fun `verifies its own hashes`() {
		val password = "a password with spaces & symbols !@#"

		val hash = Argon2PasswordHasher.hash(password.toCharArray(), memoryKib = 1024, iterations = 2, parallelism = 1)

		assertTrue(Argon2PasswordHasher.verify(hash, password.toCharArray()))
		assertFalse(Argon2PasswordHasher.verify(hash, "a password with spaces & symbols !@".toCharArray()))
		assertFalse(Argon2PasswordHasher.verify(hash, "".toCharArray()))
	}

	@Test
	fun `writes the encoded form the parameters describe`() {
		val hash = Argon2PasswordHasher.hash("pw".toCharArray(), memoryKib = 4096, iterations = 5, parallelism = 3)

		val fields = hash.split("$")
		assertEquals(listOf("", "argon2i", "v=19", "m=4096,t=5,p=3"), fields.take(4))
		// Base64 without padding: 16 salt bytes and 32 hash bytes.
		assertEquals(22, fields[4].length)
		assertEquals(43, fields[5].length)
	}

	@Test
	fun `salts every hash separately`() {
		val password = "same password".toCharArray()

		val first = Argon2PasswordHasher.hash(password, memoryKib = 1024, iterations = 2, parallelism = 1)
		val second = Argon2PasswordHasher.hash(password, memoryKib = 1024, iterations = 2, parallelism = 1)

		assertNotEquals(first, second)
		assertTrue(Argon2PasswordHasher.verify(first, password))
		assertTrue(Argon2PasswordHasher.verify(second, password))
	}

	@Test
	fun `treats an unreadable stored hash as a failed verification`() {
		val salt = "c2l4dGVlbmJ5dGVzYWx0IQ"
		val hash = "l/Rt3Va9SLakmdDaJnB+MRNtwcxBR8XKsJpNKSEGnro"

		val malformed = listOf(
			"",
			"not a hash at all",
			// Bcrypt, in case a server was ever seeded from another tool.
			"\$2y\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
			// Unknown variant.
			"\$argon2x\$v=19\$m=1024,t=2,p=1\$$salt\$$hash",
			// Unsupported version.
			"\$argon2i\$v=99\$m=1024,t=2,p=1\$$salt\$$hash",
			// Missing the hash field.
			"\$argon2i\$v=19\$m=1024,t=2,p=1\$$salt",
			// Non-numeric and missing cost parameters.
			"\$argon2i\$v=19\$m=lots,t=2,p=1\$$salt\$$hash",
			"\$argon2i\$v=19\$m=1024,p=1\$$salt\$$hash",
			// Memory below the parallelism floor, which the generator silently clamps.
			"\$argon2i\$v=19\$m=4,t=2,p=4\$$salt\$$hash",
			// Costs a corrupt row could carry that would exhaust the heap or the clock.
			"\$argon2i\$v=19\$m=2000000000,t=2,p=1\$$salt\$$hash",
			"\$argon2i\$v=19\$m=1024,t=2000000000,p=1\$$salt\$$hash",
			// Keyed and associated-data hashes need inputs we don't hold.
			"\$argon2i\$v=19\$m=1024,t=2,p=1,keyid=Zm9v\$$salt\$$hash",
			// Salt outside the base64 alphabet.
			"\$argon2i\$v=19\$m=1024,t=2,p=1\$!!!!\$$hash",
		)

		malformed.forEach {
			assertFalse(
				Argon2PasswordHasher.verify(it, "legacy-format".toCharArray()),
				"Must not verify: $it",
			)
		}
	}
}
