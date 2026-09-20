package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseInfoTest {

	private fun releaseInfo(autoPublish: Boolean) = ReleaseInfo(
		semVar = SemVar(1, 2, 3),
		changeLog = "Added a thing\n- detail one",
		storeChangeLog = "Added a thing",
		platforms = Platform.ALL,
		autoPublish = autoPublish,
	)

	@Test
	fun `Auto-publish is off by default`() {
		assertFalse(
			ReleaseInfo(
				semVar = SemVar(1, 2, 3),
				changeLog = "notes",
				storeChangeLog = "notes",
				platforms = Platform.ALL,
			).autoPublish
		)
	}

	@Test
	fun `Tag message is the changelog alone when auto-publish is off`() {
		assertEquals("Added a thing\n- detail one", releaseInfo(autoPublish = false).tagMessage)
	}

	@Test
	fun `Tag message appends the trailer as its own last line when auto-publish is on`() {
		val lines = releaseInfo(autoPublish = true).tagMessage.lines()
		assertEquals(AUTO_PUBLISH_TRAILER, lines.last())
		// The blank line keeps the trailer out of the last changelog paragraph, so
		// set-release-body strips it without leaving a dangling line in the body.
		assertEquals("", lines[lines.size - 2])
	}

	// set-release-body greps for this exact whole line; a reword here silently stops
	// CI from publishing.
	@Test
	fun `Trailer wire format is stable`() {
		assertEquals("Auto-Publish: true", AUTO_PUBLISH_TRAILER)
	}

	@Test
	fun `Dropping the trailer restores the plain changelog`() {
		val stripped = releaseInfo(autoPublish = true).tagMessage
			.lines()
			.filterNot { it == AUTO_PUBLISH_TRAILER }
			.joinToString("\n")
			.trimEnd()
		assertEquals(releaseInfo(autoPublish = false).tagMessage, stripped)
	}

	@Test
	fun `Trailer never leaks into a release that did not ask for it`() {
		assertTrue(AUTO_PUBLISH_TRAILER !in releaseInfo(autoPublish = false).tagMessage)
	}
}
