package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class WritingSessionLifecycleTest {

	private val utc = TimeZone.UTC

	private fun at(iso: String): Instant = Instant.parse(iso)

	@Test
	fun `first delta opens a new session`() {
		val result = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T09:00:00Z"),
			tz = utc,
		)
		assertEquals(1, result.size)
		assertEquals(50, result.single().wordsWritten)
		assertFalse(result.single().sealed)
	}

	@Test
	fun `delta within MERGE_GAP same day extends the open session`() {
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T09:00:00Z"),
			tz = utc,
		)
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 30,
			at = at("2026-04-28T11:00:00Z"),
			tz = utc,
		)
		assertEquals(1, second.size)
		assertEquals(80, second.single().wordsWritten)
		assertEquals(at("2026-04-28T11:00:00Z"), second.single().endedAt)
		assertFalse(second.single().sealed)
	}

	@Test
	fun `delta past MERGE_GAP seals previous and opens new`() {
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T09:00:00Z"),
			tz = utc,
		)
		// 7 hours later — exceeds MERGE_GAP (6 hours)
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 30,
			at = at("2026-04-28T16:00:00Z"),
			tz = utc,
		)
		assertEquals(2, second.size)
		assertTrue(second.first().sealed)
		assertEquals(50, second.first().wordsWritten)
		assertFalse(second.last().sealed)
		assertEquals(30, second.last().wordsWritten)
	}

	@Test
	fun `calendar-day rollover seals previous even within MERGE_GAP`() {
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T23:30:00Z"),
			tz = utc,
		)
		// Only 1 hour later — within MERGE_GAP — but new calendar day in UTC.
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 30,
			at = at("2026-04-29T00:30:00Z"),
			tz = utc,
		)
		assertEquals(2, second.size)
		assertTrue(second.first().sealed)
		assertFalse(second.last().sealed)
	}

	@Test
	fun `delta after a sealed session always opens a new one`() {
		val sealedSession = WritingSession(
			startedAt = at("2026-04-27T09:00:00Z"),
			endedAt = at("2026-04-27T11:00:00Z"),
			wordsWritten = 200,
			sealed = true,
		)
		val result = WritingSessionTracker.mergeWriting(
			current = listOf(sealedSession),
			words = 25,
			at = at("2026-04-27T11:30:00Z"),
			tz = utc,
		)
		assertEquals(2, result.size)
		assertTrue(result.first().sealed)
		assertEquals(200, result.first().wordsWritten)
		assertEquals(25, result.last().wordsWritten)
	}

	@Test
	fun `MERGE_GAP boundary is greater-than not greater-or-equal`() {
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T09:00:00Z"),
			tz = utc,
		)
		// Exactly MERGE_GAP — should still merge, not seal.
		val gapEdge = at("2026-04-28T09:00:00Z") + WritingSessionTracker.MERGE_GAP
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 10,
			at = gapEdge,
			tz = utc,
		)
		assertEquals(1, second.size)
		assertEquals(60, second.single().wordsWritten)
	}

	@Test
	fun `merge respects timezone for calendar-day decision`() {
		// UTC midnight crossing, but in a tz where it's still the same day.
		val tz = TimeZone.of("America/Los_Angeles") // UTC-7/-8
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			// 9pm Pacific on Apr 28
			at = at("2026-04-29T04:00:00Z"),
			tz = tz,
		)
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 25,
			// 11pm Pacific on Apr 28 (still same local day)
			at = at("2026-04-29T06:00:00Z"),
			tz = tz,
		)
		assertEquals(1, second.size, "Same local day in user's tz should merge")
		assertEquals(75, second.single().wordsWritten)
	}

	@Test
	fun `gap shorter than MERGE_GAP keeps extending the same session`() {
		val first = WritingSessionTracker.mergeWriting(
			current = emptyList(),
			words = 50,
			at = at("2026-04-28T09:00:00Z"),
			tz = utc,
		)
		val gapWithinMerge =
			at("2026-04-28T09:00:00Z") + 35.minutes
		val second = WritingSessionTracker.mergeWriting(
			current = first,
			words = 10,
			at = gapWithinMerge,
			tz = utc,
		)
		assertEquals(1, second.size)
		assertFalse(second.single().sealed)
	}

	@Test
	fun `MERGE_GAP default is locked at 6 hours`() {
		assertEquals(6.hours, WritingSessionTracker.MERGE_GAP)
	}
}
