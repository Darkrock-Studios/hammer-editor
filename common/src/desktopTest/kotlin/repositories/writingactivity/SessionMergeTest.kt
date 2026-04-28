package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.writingactivity.mergeOwnSlotSessions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SessionMergeTest {

	private fun session(
		startedAt: String,
		endedAt: String = startedAt,
		wordsWritten: Int,
		sealed: Boolean = false,
	) = WritingSession(
		startedAt = Instant.parse(startedAt),
		endedAt = Instant.parse(endedAt),
		wordsWritten = wordsWritten,
		sealed = sealed,
	)

	@Test
	fun `empty inputs yield empty output`() {
		assertTrue(mergeOwnSlotSessions(emptyList(), emptyList()).isEmpty())
	}

	@Test
	fun `local-only sessions pass through sorted`() {
		val a = session("2026-04-28T10:00:00Z", wordsWritten = 50)
		val b = session("2026-04-28T08:00:00Z", wordsWritten = 30)
		val merged = mergeOwnSlotSessions(local = listOf(a, b), remote = emptyList())
		assertEquals(listOf(b, a), merged, "Should be sorted by startedAt")
	}

	@Test
	fun `remote-only sessions pass through sorted`() {
		val a = session("2026-04-28T10:00:00Z", wordsWritten = 50)
		val b = session("2026-04-28T08:00:00Z", wordsWritten = 30)
		val merged = mergeOwnSlotSessions(local = emptyList(), remote = listOf(a, b))
		assertEquals(listOf(b, a), merged)
	}

	@Test
	fun `disjoint sessions union without conflict`() {
		val morning = session("2026-04-28T09:00:00Z", wordsWritten = 50)
		val afternoon = session("2026-04-28T14:00:00Z", wordsWritten = 80)
		val merged = mergeOwnSlotSessions(local = listOf(morning), remote = listOf(afternoon))
		assertEquals(listOf(morning, afternoon), merged)
	}

	@Test
	fun `same startedAt picks the higher wordsWritten`() {
		val low = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T10:00:00Z", wordsWritten = 50)
		val high = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T10:30:00Z", wordsWritten = 75)
		val merged = mergeOwnSlotSessions(local = listOf(low), remote = listOf(high))
		assertEquals(1, merged.size)
		assertEquals(75, merged.single().wordsWritten)
	}

	@Test
	fun `same startedAt picks the later endedAt`() {
		val earlier = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T10:00:00Z", wordsWritten = 75)
		val later = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T11:00:00Z", wordsWritten = 50)
		val merged = mergeOwnSlotSessions(local = listOf(earlier), remote = listOf(later))
		assertEquals(1, merged.size)
		assertEquals(Instant.parse("2026-04-28T11:00:00Z"), merged.single().endedAt)
		// Higher wordsWritten still wins independently of endedAt.
		assertEquals(75, merged.single().wordsWritten)
	}

	@Test
	fun `sealed flag is sticky on either side`() {
		val unsealed = session("2026-04-28T09:00:00Z", wordsWritten = 50)
		val sealedRemote = session("2026-04-28T09:00:00Z", wordsWritten = 50, sealed = true)
		val merged = mergeOwnSlotSessions(local = listOf(unsealed), remote = listOf(sealedRemote))
		assertTrue(merged.single().sealed)
	}

	@Test
	fun `merge is symmetric`() {
		val a = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T10:00:00Z", wordsWritten = 50)
		val b = session("2026-04-28T09:00:00Z", endedAt = "2026-04-28T11:00:00Z", wordsWritten = 80, sealed = true)
		val ab = mergeOwnSlotSessions(local = listOf(a), remote = listOf(b))
		val ba = mergeOwnSlotSessions(local = listOf(b), remote = listOf(a))
		assertEquals(ab, ba)
	}

	@Test
	fun `local AB plus server B-larger and C produces A B-larger C`() {
		val a = session("2026-04-28T08:00:00Z", wordsWritten = 25)
		val bSmall = session("2026-04-28T10:00:00Z", endedAt = "2026-04-28T10:30:00Z", wordsWritten = 60)
		val bLarge = session("2026-04-28T10:00:00Z", endedAt = "2026-04-28T11:00:00Z", wordsWritten = 80)
		val c = session("2026-04-28T15:00:00Z", wordsWritten = 200)

		val merged = mergeOwnSlotSessions(
			local = listOf(a, bSmall),
			remote = listOf(bLarge, c),
		)

		assertEquals(3, merged.size)
		assertEquals(a, merged[0])
		assertEquals(80, merged[1].wordsWritten)
		assertEquals(Instant.parse("2026-04-28T11:00:00Z"), merged[1].endedAt)
		assertEquals(c, merged[2])
	}
}
