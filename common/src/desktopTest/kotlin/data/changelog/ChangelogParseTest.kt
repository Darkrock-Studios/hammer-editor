package data.changelog

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.data.changelog.parseChangelog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangelogParseTest {

	@Test
	fun `parses version date and notes from the header`() {
		val changelog = parseChangelog(
			"""
			## [3.7.2] - 2026-7-27

			[Improve]
			- Web: Redesign home page
			""".trimIndent()
		)

		assertEquals("3.7.2", changelog?.version)
		assertEquals("2026-7-27", changelog?.date)
		assertEquals("[Improve]\n- Web: Redesign home page", changelog?.notes)
	}

	@Test
	fun `header without a date still parses`() {
		val changelog = parseChangelog("## [3.7.2]\n\n- A thing")

		assertEquals("3.7.2", changelog?.version)
		assertNull(changelog?.date)
		assertEquals("- A thing", changelog?.notes)
	}

	@Test
	fun `text with no header falls back to the running version`() {
		val changelog = parseChangelog("- Just some notes")

		assertEquals(BuildMetadata.APP_VERSION, changelog?.version)
		assertNull(changelog?.date)
		assertEquals("- Just some notes", changelog?.notes)
	}

	@Test
	fun `blank text is not a changelog`() {
		assertNull(parseChangelog(""))
		assertNull(parseChangelog("   \n  \n"))
	}
}
