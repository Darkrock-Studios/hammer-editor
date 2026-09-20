package com.darkrockstudios.apps.hammer.base

import kotlin.test.Test
import kotlin.test.assertEquals

class DistributionChannelTest {

	/**
	 * Pins the tokens against the build-side enum in
	 * `buildSrc/src/main/java/distributionChannel.kt`. A channel added or renamed on one side and
	 * not the other would otherwise ship a build whose reported channel silently falls back to
	 * [DistributionChannel.DEV].
	 */
	@Test
	fun `tokens match the build side enum`() {
		val expected = listOf(
			"dev",
			"github",
			"google-play",
			"fdroid",
			"snap",
			"appimage",
			"flathub",
			"ms-store",
			"mac-app-store",
			"ios-app-store",
		)
		assertEquals(expected, DistributionChannel.entries.map { it.token })
	}

	@Test
	fun `current resolves the baked in token`() {
		assertEquals(BuildMetadata.CHANNEL, DistributionChannel.current.token)
	}
}
