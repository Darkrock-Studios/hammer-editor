package repositories

import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExampleProjectActivityGeneratorTest {

	private val tz = TimeZone.UTC
	private val now = LocalDateTime(2026, 5, 12, 15, 0).toInstant(tz)

	@Test
	fun `generates a non-empty list`() {
		val sessions = ExampleProjectRepository.generateExampleSessions(now, tz)
		assertTrue(sessions.isNotEmpty(), "expected fabricated sessions, got none")
	}

	@Test
	fun `every session is sealed so stats include them`() {
		val sessions = ExampleProjectRepository.generateExampleSessions(now, tz)
		assertTrue(sessions.all { it.sealed }, "all fabricated sessions must be sealed")
	}

	@Test
	fun `all sessions fall within the configured window`() {
		val sessions = ExampleProjectRepository.generateExampleSessions(now, tz)
		val today = now.toLocalDateTime(tz).date
		val earliestDay = today.minus(ExampleProjectRepository.EXAMPLE_DAYS - 1, DateTimeUnit.DAY)
		val outliers = sessions.filterNot {
			it.startedAt.toLocalDateTime(tz).date in earliestDay..today
		}
		assertTrue(
			outliers.isEmpty(),
			"sessions outside ${ExampleProjectRepository.EXAMPLE_DAYS}-day window: $outliers",
		)
	}

	@Test
	fun `rest days exist between writing days`() {
		val sessions = ExampleProjectRepository.generateExampleSessions(now, tz)
		val writingDates = sessions
			.map { it.startedAt.toLocalDateTime(tz).date }
			.toSortedSet()
		val gaps = writingDates.zipWithNext { a, b -> b.toEpochDays() - a.toEpochDays() }
		assertTrue(
			gaps.any { it >= 2 },
			"expected at least one rest gap of >= 2 days, got gaps=$gaps",
		)
	}

	@Test
	fun `today is always a writing day`() {
		val sessions = ExampleProjectRepository.generateExampleSessions(now, tz)
		val today = now.toLocalDateTime(tz).date
		assertTrue(
			sessions.any { it.startedAt.toLocalDateTime(tz).date == today },
			"expected at least one session on $today",
		)
	}

	@Test
	fun `same seed produces identical output`() {
		val a = ExampleProjectRepository.generateExampleSessions(now, tz, seed = 12345L)
		val b = ExampleProjectRepository.generateExampleSessions(now, tz, seed = 12345L)
		assertEquals(a, b)
	}
}
