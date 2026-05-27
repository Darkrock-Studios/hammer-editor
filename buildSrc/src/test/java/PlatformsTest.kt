package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformsTest {

	@Test
	fun `Full release produces no suffix`() {
		assertEquals("", tagSuffix(Platform.ALL))
	}

	@Test
	fun `Single store produces single token suffix`() {
		assertEquals("+google-play", tagSuffix(setOf(Platform.GOOGLE_PLAY)))
		assertEquals("+fdroid", tagSuffix(setOf(Platform.FDROID)))
		assertEquals("+ios-app-store", tagSuffix(setOf(Platform.IOS_APP_STORE)))
	}

	@Test
	fun `Multiple stores produce tokens in enum order regardless of input order`() {
		val expected = "+google-play+ms-store"
		assertEquals(expected, tagSuffix(setOf(Platform.GOOGLE_PLAY, Platform.MS_STORE)))
		// Same set, different declaration order in the call — same suffix.
		assertEquals(expected, tagSuffix(setOf(Platform.MS_STORE, Platform.GOOGLE_PLAY)))
	}

	@Test
	fun `Empty platform set is rejected`() {
		assertFailsWith<IllegalArgumentException> { tagSuffix(emptySet()) }
	}

	@Test
	fun `Every platform has a unique tagToken`() {
		val tokens = Platform.values().map { it.tagToken }
		assertEquals(tokens.size, tokens.toSet().size, "Duplicate tagToken in Platform enum: $tokens")
	}

	@Test
	fun `isPlatformReleaseTag matches the bare version`() {
		assertEquals(true, isPlatformReleaseTag("v1.2.4", "v1.2.4"))
	}

	@Test
	fun `isPlatformReleaseTag matches single-platform tags`() {
		assertEquals(true, isPlatformReleaseTag("v1.2.4+google-play", "v1.2.4"))
		assertEquals(true, isPlatformReleaseTag("v1.2.4+fdroid", "v1.2.4"))
	}

	@Test
	fun `isPlatformReleaseTag matches multi-platform tags`() {
		assertEquals(true, isPlatformReleaseTag("v1.2.4+google-play+fdroid", "v1.2.4"))
		assertEquals(true, isPlatformReleaseTag("v1.2.4+ms-store+mac-app-store+ios-app-store", "v1.2.4"))
	}

	@Test
	fun `isPlatformReleaseTag rejects unknown tokens (rc1, sbom, etc)`() {
		assertEquals(false, isPlatformReleaseTag("v1.2.4+rc1", "v1.2.4"))
		assertEquals(false, isPlatformReleaseTag("v1.2.4+sbom", "v1.2.4"))
		// Mixed known + unknown is rejected — don't risk deleting a tag we
		// don't fully understand.
		assertEquals(false, isPlatformReleaseTag("v1.2.4+google-play+rc1", "v1.2.4"))
	}

	@Test
	fun `isPlatformReleaseTag rejects tags for a different version`() {
		assertEquals(false, isPlatformReleaseTag("v1.2.5", "v1.2.4"))
		assertEquals(false, isPlatformReleaseTag("v1.2.5+google-play", "v1.2.4"))
	}

	@Test
	fun `isPlatformReleaseTag rejects empty suffix`() {
		assertEquals(false, isPlatformReleaseTag("v1.2.4+", "v1.2.4"))
	}
}
