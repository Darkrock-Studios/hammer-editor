package com.darkrockstudios.apps.hammer.account.mfa

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotpTest {
	private val rfcSecret = "12345678901234567890".encodeToByteArray()

	@Test
	fun `RFC 6238 SHA1 vectors at 8 digits`() {
		val vectors = listOf(
			59L to "94287082",
			1111111109L to "07081804",
			1111111111L to "14050471",
			1234567890L to "89005924",
			2000000000L to "69279037",
			20000000000L to "65353130",
		)
		vectors.forEach { (time, expected) ->
			assertEquals(expected, Totp.code(rfcSecret, Totp.stepAt(time), digits = 8))
		}
	}

	@Test
	fun `Six digit codes are the low digits`() {
		assertEquals("287082", Totp.code(rfcSecret, Totp.stepAt(59L)))
		assertEquals("005924", Totp.code(rfcSecret, Totp.stepAt(1234567890L)))
	}

	@Test
	fun `Accepts one step of drift either way`() {
		val now = 1234567890L
		val step = Totp.stepAt(now)
		assertEquals(step - 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 1), now, lastStep = null))
		assertEquals(step, Totp.verify(rfcSecret, Totp.code(rfcSecret, step), now, lastStep = null))
		assertEquals(step + 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 1), now, lastStep = null))
	}

	@Test
	fun `Rejects codes outside the drift window`() {
		val now = 1234567890L
		val step = Totp.stepAt(now)
		assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 2), now, lastStep = null))
		assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 2), now, lastStep = null))
	}

	@Test
	fun `Rejects a replayed or older step`() {
		val now = 1234567890L
		val step = Totp.stepAt(now)
		val code = Totp.code(rfcSecret, step)
		assertNull(Totp.verify(rfcSecret, code, now, lastStep = step))
		assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 1), now, lastStep = step))
		assertEquals(step + 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 1), now, lastStep = step))
	}

	@Test
	fun `Rejects malformed codes`() {
		val now = 1234567890L
		assertNull(Totp.verify(rfcSecret, "", now, lastStep = null))
		assertNull(Totp.verify(rfcSecret, "12345", now, lastStep = null))
		assertNull(Totp.verify(rfcSecret, "abcdef", now, lastStep = null))
	}

	@Test
	fun `Tolerates spaces in the code`() {
		val now = 1234567890L
		assertEquals(Totp.stepAt(now), Totp.verify(rfcSecret, "005 924", now, lastStep = null))
	}

	@Test
	fun `New secrets are 20 bytes and distinct`() {
		val a = Totp.newSecret()
		val b = Totp.newSecret()
		assertEquals(20, a.size)
		assertTrue(!a.contentEquals(b))
	}

	@Test
	fun `Base32 matches RFC 4648 and round trips`() {
		assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32Encode(rfcSecret))
		assertEquals("MZXW6YQ", Totp.base32Encode("foob".encodeToByteArray()))
		assertTrue(rfcSecret.contentEquals(Totp.base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")))
		assertTrue("foob".encodeToByteArray().contentEquals(Totp.base32Decode("mzxw 6yq=")))
	}

	@Test
	fun `otpauth URI carries issuer, label and secret`() {
		val uri = Totp.otpauthUri(rfcSecret, issuer = "My Hammer", accountName = "a+b@example.com")
		assertEquals(
			"otpauth://totp/My%20Hammer:a%2Bb%40example.com" +
				"?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=My%20Hammer&algorithm=SHA1&digits=6&period=30",
			uri
		)
	}
}
