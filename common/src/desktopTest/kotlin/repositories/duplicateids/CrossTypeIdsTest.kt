package repositories.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossTypeIdsTest : RationalizerTestBase() {

	@Test
	fun `A scene and an entry with one id are found`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		write("encyclopedia/person/person~6~Bob.toml", entryToml(6, "Bob", "A blacksmith"))
		write("encyclopedia/person/person~7~Alice.toml", entryToml(7, "Alice", "A fencer"))

		assertEquals(mapOf(6 to listOf(EntityType.EncyclopediaEntry, EntityType.Scene)), rationalizer.findCrossTypeIds())
	}

	@Test
	fun `Every type takes part, timeline events included`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		write("scenes/.drafts/6/6~6~Take one~1700000000.md", "First take")
		write("notes/note-6.toml", noteToml(6, "Remember the sword"))
		write("timeline/timeline.toml", toml.encodeToString(TimeLineContainer(listOf(TimeLineEvent(id = 6, order = 0, content = "The duel")))))

		val owners = rationalizer.findCrossTypeIds()

		assertEquals(
			setOf(EntityType.Scene, EntityType.SceneDraft, EntityType.Note, EntityType.TimelineEvent),
			owners.getValue(6).toSet(),
		)
	}

	@Test
	fun `Copies within one type are not a cross-type collision`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		write("scenes/1~Duel copy~6.md", "Touche")

		assertTrue(rationalizer.findCrossTypeIds().isEmpty())
	}
}
