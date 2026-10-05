package com.darkrockstudios.apps.hammer.base

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageFormatTest {

	/**
	 * Pins the tokens against the build-side enum in `buildSrc/src/main/java/packageFormat.kt`,
	 * the same way [DistributionChannelTest] pins the channel tokens.
	 */
	@Test
	fun `tokens match the build side enum`() {
		val expected = listOf(
			"none",
			"msi",
			"exe",
			"deb",
			"rpm",
			"dmg",
			"pkg",
			"appimage",
			"msix",
			"snap",
			"flatpak",
		)
		assertEquals(expected, PackageFormat.entries.map { it.token })
	}

	@Test
	fun `current resolves the baked in token`() {
		assertEquals(BuildMetadata.PACKAGE_FORMAT, PackageFormat.current.token)
	}
}
