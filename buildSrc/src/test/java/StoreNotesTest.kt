package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreNotesTest {

	private val url = releaseNotesUrl("v3.7.0")

	@Test
	fun `Short changelog is kept whole and gains the footer`() {
		val changelog = "- [Fix] A bug\n- [New] A feature"

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertEquals("- [Fix] A bug\n- [New] A feature\n\nFull changelog:\n$url", notes)
	}

	@Test
	fun `Long changelog is cut at a line boundary and stays under the limit`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(notes.length <= PLAY_STORE_LIMIT, "Notes were ${notes.length} characters")
		assertTrue(notes.endsWith("\n\nFull changelog:\n$url"))
		val body = notes.substringBefore("\n\nFull changelog:").removeSuffix("…")
		body.lines().forEach { line ->
			assertTrue(line in changelog.lines(), "Cut mid-line: '$line'")
		}
	}

	@Test
	fun `Truncated notes keep the leading lines`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(notes.startsWith("- [New] Feature number 1 in this release"))
	}

	@Test
	fun `A single overlong line falls back to a word boundary`() {
		val changelog = "word ".repeat(300).trim()

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(notes.length <= PLAY_STORE_LIMIT, "Notes were ${notes.length} characters")
		assertTrue(notes.startsWith("word word"))
		assertTrue(notes.substringBefore("…").endsWith("word"))
	}

	@Test
	fun `Apple limit fits a changelog Play would truncate`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val notes = formatStoreNotes(changelog, APPLE_STORE_LIMIT, url)

		assertEquals("$changelog\n\nFull changelog:\n$url", notes)
	}

	@Test
	fun `Notes length predicts exactly when the notes are truncated`() {
		// Both the console warning and the dialog's amber counter derive "was it cut?"
		// from this comparison rather than from the formatted text.
		val bullet = "- [New] Feature number %d in this release"
		(1..40).forEach { count ->
			val changelog = (1..count).joinToString("\n") { bullet.format(it) }
			val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)
			val overLimit = storeNotesLength(changelog, url) > PLAY_STORE_LIMIT

			assertEquals(
				overLimit,
				notes.length < storeNotesLength(changelog, url),
				"at $count bullets"
			)
			assertTrue(
				notes.length <= PLAY_STORE_LIMIT,
				"at $count bullets: ${notes.length} characters"
			)
		}
	}

	@Test
	fun `A long line does not discard the budget the lines after it would fill`() {
		val longLine = "- [Fix] " + "detail ".repeat(80).trim()
		val changelog = listOf("- [New] First", longLine) + (1..10).map { "- [Fix] Short fix $it" }

		val notes = formatStoreNotes(changelog.joinToString("\n"), PLAY_STORE_LIMIT, url)

		assertTrue(notes.contains("- [Fix] Short fix 1"), notes)
		assertTrue(
			!notes.contains("detail detail"),
			"The overlong line should be skipped, not included"
		)
	}

	@Test
	fun `A blank changelog produces no notes rather than a bare link`() {
		assertEquals("", formatStoreNotes("", PLAY_STORE_LIMIT, url))
		assertEquals("", formatStoreNotes("   \n\n  ", PLAY_STORE_LIMIT, url))
		assertEquals(0, storeNotesLength("  \n ", url))
	}

	@Test
	fun `CRLF input is normalized on both the whole and truncated paths`() {
		val short = "- [Fix] A bug\r\n- [New] A feature"
		val long = (1..40).joinToString("\r\n") { "- [New] Feature number $it in this release" }

		val shortNotes = formatStoreNotes(short, PLAY_STORE_LIMIT, url)
		val longNotes = formatStoreNotes(long, PLAY_STORE_LIMIT, url)

		assertTrue(!shortNotes.contains('\r'), "Whole path kept CRLF")
		assertTrue(!longNotes.contains('\r'), "Truncated path kept CRLF")
		assertEquals(shortNotes.length, storeNotesLength(short, url))
	}

	@Test
	fun `Release url points at the tag`() {
		assertEquals(
			"https://github.com/Darkrock-Studios/hammer-editor/releases/tag/v3.7.0+google-play",
			releaseNotesUrl("v3.7.0+google-play"),
		)
	}
}
