package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.AuthorWords
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.wordsByAuthor
import com.darkrockstudios.apps.hammer.common.data.writingactivity.AttributedSlot
import com.darkrockstudios.apps.hammer.common.data.writingactivity.activityOwner
import com.darkrockstudios.apps.hammer.common.data.writingactivity.attributeSlots
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WritingActivityAttributionTest {

	private val owner = ProjectOwner(5, "Owner Pen")

	private fun signedIn(userId: Long) =
		ServerSettings(url = "example.com", email = "a@example.com", userId = userId, bearerToken = "t", refreshToken = "r")

	@Test
	fun `a slot with no author is the owner's in a shared project`() {
		val attributed = attributeSlots(
			listOf(
				WritingActivitySlot("device-a", DeviceLog("Desktop")),
				WritingActivitySlot("device-b", DeviceLog("Phone", authorId = 7, authorName = "Me")),
			),
			activityOwner(owner, signedIn(7)),
		).associate { it.deviceId to (it.authorId to it.authorName) }

		assertEquals(5L to "Owner Pen", attributed["device-a"])
		assertEquals(7L to "Me", attributed["device-b"])
	}

	@Test
	fun `a slot with no author is the signed-in account's in its own project`() {
		val slot = attributeSlots(
			listOf(WritingActivitySlot("device-a", DeviceLog("Desktop"))),
			activityOwner(null, signedIn(7)),
		).single()

		assertEquals(7L, slot.authorId)
		assertNull(slot.authorName)
	}

	@Test
	fun `with no account and no owner, unauthored slots stay unattributed`() {
		val slot = attributeSlots(listOf(WritingActivitySlot("device-a", DeviceLog("Desktop"))), activityOwner(null, null))
		assertNull(slot.single().authorId)
	}

	private fun slot(device: String, label: String, author: Long?, name: String?, words: Int, endedAt: String = "2026-04-28T10:00:00Z") =
		AttributedSlot(
			device,
			DeviceLog(label, listOf(WritingSession(Instant.parse("2026-04-28T09:00:00Z"), Instant.parse(endedAt), words))),
			author,
			name,
		)

	@Test
	fun `one author leaves the by-device view alone`() {
		assertTrue(wordsByAuthor(listOf(slot("a", "Laptop", 1, "Ada", 10), slot("b", "Phone", 1, "Ada", 20))).isEmpty())
	}

	@Test
	fun `an author with no words does not make a second group`() {
		assertTrue(wordsByAuthor(listOf(slot("a", "Laptop", 1, "Ada", 10), slot("b", "Phone", 2, "Bo", 0))).isEmpty())
	}

	@Test
	fun `two authors group by id, named from their latest slot`() {
		val authors = wordsByAuthor(
			listOf(
				slot("a", "Laptop", 1, "Old Pen", 10, endedAt = "2026-04-28T10:00:00Z"),
				slot("b", "Phone", 1, "New Pen", 5, endedAt = "2026-04-29T10:00:00Z"),
				slot("a", "Laptop", 2, null, 30),
			)
		)

		assertEquals(
			listOf(
				AuthorWords(2, null, 30, mapOf("Laptop" to 30)),
				AuthorWords(1, "New Pen", 15, mapOf("Laptop" to 10, "Phone" to 5)),
			),
			authors,
		)
	}

	@Test
	fun `a removed author keeps their name and words`() {
		val authors = wordsByAuthor(listOf(slot("a", "Laptop", 1, "Ada", 10), slot("b", "Laptop", 99, "Gone Pen", 3)))
		assertEquals("Gone Pen", authors.single { it.authorId == 99L }.authorName)
	}
}
