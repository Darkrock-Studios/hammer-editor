package synchronizer

import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncWindow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class NightlySyncWindowTest {

	private val tz = TimeZone.of("America/Los_Angeles")

	private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
		LocalDateTime(year, month, day, hour, minute).toInstant(tz)

	@Test
	fun `the window covers the small hours only`() {
		assertFalse(NightlySyncWindow.contains(at(2026, 10, 26, 0, 59), tz))
		assertTrue(NightlySyncWindow.contains(at(2026, 10, 26, 1, 0), tz))
		assertTrue(NightlySyncWindow.contains(at(2026, 10, 26, 5, 59), tz))
		assertFalse(NightlySyncWindow.contains(at(2026, 10, 26, 6, 0), tz))
		assertFalse(NightlySyncWindow.contains(at(2026, 10, 26, 14, 0), tz))
	}

	@Test
	fun `before the window opens it waits for tonight`() {
		assertEquals(30.minutes, NightlySyncWindow.untilNextStart(at(2026, 10, 26, 0, 30), tz))
	}

	@Test
	fun `once the window has opened it waits for tomorrow night`() {
		assertEquals(24.hours, NightlySyncWindow.untilNextStart(at(2026, 10, 26, 1, 0), tz))
		assertEquals(22.hours, NightlySyncWindow.untilNextStart(at(2026, 10, 26, 3, 0), tz))
		assertEquals(11.hours, NightlySyncWindow.untilNextStart(at(2026, 10, 26, 14, 0), tz))
	}

	@Test
	fun `the next start follows local time across a daylight saving change`() {
		// US clocks spring forward at 02:00 on 2026-03-08, so that day is 23 hours long.
		assertEquals(23.hours, NightlySyncWindow.untilNextStart(at(2026, 3, 8, 1, 0), tz))
	}
}
