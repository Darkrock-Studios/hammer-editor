import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.projectsync.DataChoice
import com.darkrockstudios.apps.hammer.common.projectsync.buildResolved
import org.junit.Test
import kotlin.test.assertEquals

class ProjectDataConflictResolveTest {

	private val local = ProjectData(authorName = "Local", coverImageId = 1, dictionaryWords = setOf("a"))
	private val server = ProjectData(authorName = "Server", coverImageId = null, dictionaryWords = setOf("b"))

	private fun resolve(author: DataChoice, cover: DataChoice) = buildResolved(
		local = local,
		server = server,
		authorChoice = author,
		themeChoice = DataChoice.LOCAL,
		goalChoice = DataChoice.LOCAL,
		tagsChoice = DataChoice.LOCAL,
		languageChoice = DataChoice.LOCAL,
		dictionaryChoice = DataChoice.LOCAL,
		coverChoice = cover,
	)

	@Test
	fun `cover follows its own choice`() {
		assertEquals(1, resolve(DataChoice.REMOTE, DataChoice.LOCAL).coverImageId)
		assertEquals(null, resolve(DataChoice.LOCAL, DataChoice.REMOTE).coverImageId)
	}

	@Test
	fun `other fields are independent of the cover choice`() {
		val resolved = resolve(DataChoice.REMOTE, DataChoice.LOCAL)
		assertEquals("Server", resolved.authorName)
		assertEquals(setOf("a", "b"), resolved.dictionaryWords)
	}
}
