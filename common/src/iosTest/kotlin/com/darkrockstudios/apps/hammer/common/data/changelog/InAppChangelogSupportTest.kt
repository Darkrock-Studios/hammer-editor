package com.darkrockstudios.apps.hammer.common.data.changelog

import kotlin.test.Test
import kotlin.test.assertFalse

class InAppChangelogSupportTest {

	// Apple rejects the baked changelog under guideline 2.3.10 for naming other platforms.
	@Test
	fun `in-app changelog is never shown on iOS`() {
		assertFalse(supportsInAppChangelog)
	}
}
