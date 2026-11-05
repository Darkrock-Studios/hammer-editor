package repositories.timeline

import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.parseStoryDateText
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.toCompactText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryDateTextTest {

	@Test
	fun `A date round trips through its compact text`() {
		val date = StoryDate(mapOf("year" to -97L, "day" to 210L, "hour" to 23L, "minute" to 5L))

		val parsed = parseStoryDateText(date.toCompactText())

		assertEquals(date, parsed)
	}

	@Test
	fun `Rungs are written in calendar chain order then unknown units alphabetically`() {
		val date = StoryDate(
			mapOf(
				"minute" to 1L,
				"zeta" to 7L,
				"day" to 3L,
				"alpha" to 6L,
				"year" to 97L,
				"hour" to 2L,
				"month" to 4L,
			)
		)

		assertEquals("year=97 month=4 day=3 hour=2 minute=1 alpha=6 zeta=7", date.toCompactText())
	}

	@Test
	fun `Parsing tolerates surrounding and repeated whitespace`() {
		assertEquals(
			StoryDate(mapOf("year" to 97L, "day" to 210L)),
			parseStoryDateText("  year=97 \t day=210\n"),
		)
	}

	@Test
	fun `Blank text parses to null`() {
		assertNull(parseStoryDateText(""))
		assertNull(parseStoryDateText("   "))
	}

	@Test
	fun `Malformed text parses to null`() {
		assertNull(parseStoryDateText("year"))
		assertNull(parseStoryDateText("year="))
		assertNull(parseStoryDateText("=97"))
		assertNull(parseStoryDateText("year=ninety"))
		assertNull(parseStoryDateText("year=97 day"))
		assertNull(parseStoryDateText("year=9.5"))
		assertNull(parseStoryDateText("year=1=2"))
		assertNull(parseStoryDateText("year=97 year=98"))
		assertNull(parseStoryDateText("year=99999999999999999999"))
	}
}
