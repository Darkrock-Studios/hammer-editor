package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreNotesTest {

	private val url = releaseNotesUrl("v3.7.0")

	/** The notes without the footer or the truncation mark. */
	private fun publishedBody(notes: String) =
		notes.substringBefore("\n\nFull changelog:").removeSuffix("…")

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
	fun `A skipped bullet takes its wrapped continuation lines with it`() {
		val overlong = "- [Improve] " + "detail ".repeat(80).trim()
		val changelog = listOf(
			"- [New] First",
			overlong,
			"two-tier footer",
		) + (1..10).map { "- [Fix] Short fix $it" }

		val notes = formatStoreNotes(changelog.joinToString("\n"), PLAY_STORE_LIMIT, url)

		assertTrue(
			!notes.contains("two-tier footer"),
			"Orphaned a continuation line from its skipped bullet:\n$notes",
		)
	}

	@Test
	fun `A section header whose bullets were all skipped is dropped`() {
		val overlong = "- " + "detail ".repeat(80).trim()
		val changelog = listOf("[New]", "- Something short", "[Fix]", overlong)

		val notes = formatStoreNotes(changelog.joinToString("\n"), PLAY_STORE_LIMIT, url)

		assertTrue(!notes.contains("[Fix]"), "Kept an empty section header:\n$notes")
		assertTrue(notes.contains("[New]"), notes)
		assertTrue(notes.contains("- Something short"), notes)
	}

	@Test
	fun `A trailing section header with nothing under it is dropped`() {
		val changelog = (1..30).joinToString("\n") { "- [New] Feature number $it in this release" } +
			"\n\n[Fix]\n- A fix that will not fit"

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(!notes.contains("[Fix]"), "Kept a dangling section header:\n$notes")
	}

	@Test
	fun `Notes that lose only blank lines are not marked as truncated`() {
		val bullets = (1..8).map { "- [New] Feature number $it in this release" }
		val changelog = bullets.joinToString("\n".repeat(20))
		assertTrue(
			storeNotesLength(changelog, url) > PLAY_STORE_LIMIT,
			"The blank lines no longer push this over the limit, so nothing is exercised",
		)

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(!notes.contains("…"), "Claimed truncation when nothing was dropped:\n$notes")
		bullets.forEach { assertTrue(notes.contains(it), "Dropped $it:\n$notes") }
	}

	@Test
	fun `A section whose bullets all get skipped changes nothing else`() {
		// Its header is pruned from the published notes, so it must not spend budget
		// the surrounding sections could have used.
		val head = listOf("[New]") +
			(1..7).map { "- Feature number $it in this release, described briefly" }
		val skipped = listOf("", "[Fix]", "- " + "detail ".repeat(80).trim())
		val tail = listOf("", "[Improve]", "- Sharper icons")

		val without = formatStoreNotes((head + tail).joinToString("\n"), PLAY_STORE_LIMIT, url)
		val with = formatStoreNotes((head + skipped + tail).joinToString("\n"), PLAY_STORE_LIMIT, url)

		assertTrue(!with.contains("[Fix]"), "Kept a pruned header:\n$with")
		assertEquals(publishedBody(without), publishedBody(with))
	}

	@Test
	fun `The hard cut fallback does not leave whitespace before the ellipsis`() {
		val changelog = "word  ".repeat(300).trim()

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(
			!notes.substringBefore("…").endsWith(" "),
			"Trailing whitespace before the truncation mark:\n$notes",
		)
	}

	@Test
	fun `Only a section header fitting still produces usable notes`() {
		val changelog = "[New]\n- " + "detail ".repeat(90).trim() + "\n- " + "other ".repeat(90).trim()

		val notes = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)

		assertTrue(notes.length <= PLAY_STORE_LIMIT, "Notes were ${notes.length} characters")
		assertTrue(notes.contains("detail"), "Published nothing but a header:\n$notes")
		assertTrue(!notes.substringBefore("…").endsWith(" "), notes)
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
	fun `A null url publishes the notes with no footer and no link`() {
		val changelog = "- [Fix] A bug\n- [New] A feature"

		val notes = formatStoreNotes(changelog, APPLE_STORE_LIMIT, null)

		assertEquals(changelog, notes)
		assertEquals(changelog.length, storeNotesLength(changelog, null))
	}

	@Test
	fun `Apple notes never carry a link that App Store review would reject`() {
		// The regression that broke every iOS and macOS submission from v3.7.0: the
		// footer was appended for Apple too, so the notes ended in a github.com URL.
		val changelog = (1..400).joinToString("\n") { "- [New] Feature number $it in this release" }

		listOf(
			formatStoreNotes("- [Fix] A bug", APPLE_STORE_LIMIT, null),
			formatStoreNotes(changelog, APPLE_STORE_LIMIT, null),
			formatStoreNotes("word ".repeat(2000).trim(), APPLE_STORE_LIMIT, null),
		).forEach { notes ->
			assertTrue(!notes.contains("github.com"), "Apple notes carry a GitHub link:\n$notes")
			assertTrue(!notes.contains("Full changelog:"), "Apple notes carry the footer:\n$notes")
			assertTrue(notes.length <= APPLE_STORE_LIMIT, "Notes were ${notes.length} characters")
		}
	}

	@Test
	fun `Dropping the footer gives the body more of the budget`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val withFooter = formatStoreNotes(changelog, PLAY_STORE_LIMIT, url)
		val without = formatStoreNotes(changelog, PLAY_STORE_LIMIT, null)

		assertTrue(
			publishedBody(without).length > publishedBody(withFooter).length,
			"The footer's characters were not returned to the body",
		)
		assertTrue(without.length <= PLAY_STORE_LIMIT, "Notes were ${without.length} characters")
	}

	@Test
	fun `Release url points at the tag`() {
		assertEquals(
			"https://github.com/Darkrock-Studios/hammer-editor/releases/tag/v3.7.0+google-play",
			releaseNotesUrl("v3.7.0+google-play"),
		)
	}
}
