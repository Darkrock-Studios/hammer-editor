package repositories.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.deriveWritingStats
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WritingActivityStatsTest {

	private val today = LocalDate(2026, 4, 28) // Tuesday

	@Test
	fun `empty input returns empty derived`() {
		val derived = deriveWritingStats(emptyMap(), today)
		assertEquals(0, derived.wordsToday)
		assertEquals(0, derived.wordsThisWeek)
		assertEquals(0, derived.wordsLastWeek)
		assertNull(derived.weekChangePercent)
		assertEquals(0, derived.currentStreak)
		assertEquals(0, derived.longestStreak)
		assertEquals(0, derived.daysWritten)
		assertNull(derived.bestDayInStreak)
	}

	@Test
	fun `single day today gives streak of one and today's words`() {
		val totals = mapOf(today to 500)
		val derived = deriveWritingStats(totals, today)
		assertEquals(500, derived.wordsToday)
		assertEquals(500, derived.wordsThisWeek)
		assertEquals(1, derived.currentStreak)
		assertEquals(1, derived.longestStreak)
		assertEquals(1, derived.daysWritten)
		// streak of 1 hides best day
		assertNull(derived.bestDayInStreak)
	}

	@Test
	fun `streak counts back consecutive days ending today`() {
		val totals = mapOf(
			today to 100,
			today.minusDays(1) to 200,
			today.minusDays(2) to 150,
			today.minusDays(3) to 50,
			// gap on -4
			today.minusDays(5) to 999,
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(4, derived.currentStreak)
	}

	@Test
	fun `streak still counts when today is empty but yesterday wrote`() {
		val totals = mapOf(
			today.minusDays(1) to 200,
			today.minusDays(2) to 150,
			today.minusDays(3) to 50,
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(3, derived.currentStreak)
	}

	@Test
	fun `streak is zero when today and yesterday are both empty`() {
		val totals = mapOf(
			today.minusDays(2) to 200,
			today.minusDays(3) to 100,
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(0, derived.currentStreak)
	}

	@Test
	fun `longest streak finds longest run anywhere in history`() {
		val totals = mutableMapOf<LocalDate, Int>()
		// 5-day run far in the past
		for (i in 30..34) totals[today.minusDays(i)] = 100
		// 2-day current streak
		totals[today.minusDays(1)] = 50
		totals[today] = 60
		val derived = deriveWritingStats(totals, today)
		assertEquals(2, derived.currentStreak)
		assertEquals(5, derived.longestStreak)
	}

	@Test
	fun `this week is rolling 7 days inclusive of today`() {
		val totals = mapOf(
			today to 100,
			today.minusDays(1) to 100,
			today.minusDays(6) to 100, // edge of window
			today.minusDays(7) to 999, // outside window
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(300, derived.wordsThisWeek)
		assertEquals(999, derived.wordsLastWeek)
	}

	@Test
	fun `week change percent is null when last week is zero`() {
		val totals = mapOf(today to 500)
		val derived = deriveWritingStats(totals, today)
		assertNull(derived.weekChangePercent)
	}

	@Test
	fun `week change percent computes correctly with positive change`() {
		val totals = mapOf(
			today to 1000,
			today.minusDays(8) to 500,
		)
		val derived = deriveWritingStats(totals, today)
		// thisWeek=1000, lastWeek=500 -> +100%
		assertEquals(100, derived.weekChangePercent)
	}

	@Test
	fun `week change percent computes correctly with negative change`() {
		val totals = mapOf(
			today to 250,
			today.minusDays(8) to 1000,
		)
		val derived = deriveWritingStats(totals, today)
		// thisWeek=250, lastWeek=1000 -> -75%
		assertEquals(-75, derived.weekChangePercent)
	}

	@Test
	fun `best day in streak picks max within streak window`() {
		val totals = mapOf(
			today to 100,
			today.minusDays(1) to 800, // best
			today.minusDays(2) to 200,
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(3, derived.currentStreak)
		val best = derived.bestDayInStreak
		assertNotNull(best)
		assertEquals(today.minusDays(1), best.date)
		assertEquals(800, best.words)
	}

	@Test
	fun `weekday and weekend averages partition by day`() {
		// Today (Apr 28 2026) is a Tuesday.
		// Build: 3 weekdays at 600 each, 2 weekend days at 1000 each.
		val totals = mapOf(
			LocalDate(2026, 4, 27) to 600, // Mon
			LocalDate(2026, 4, 28) to 600, // Tue
			LocalDate(2026, 4, 29) to 600, // Wed
			LocalDate(2026, 4, 25) to 1000, // Sat
			LocalDate(2026, 4, 26) to 1000, // Sun
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(600, derived.avgWeekday)
		assertEquals(1000, derived.avgWeekend)
	}

	@Test
	fun `zero-value entries do not count as written days`() {
		val totals = mapOf(
			today to 0,
			today.minusDays(1) to 0,
			today.minusDays(2) to 100,
		)
		val derived = deriveWritingStats(totals, today)
		assertEquals(0, derived.currentStreak)
		assertEquals(1, derived.daysWritten)
	}

	private fun LocalDate.minusDays(days: Int): LocalDate =
		this.minus(days, DateTimeUnit.DAY)
}
