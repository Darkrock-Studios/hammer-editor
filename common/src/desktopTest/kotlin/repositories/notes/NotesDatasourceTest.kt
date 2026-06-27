package repositories.notes

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotesDatasourceTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: NotesDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin()
		datasource = NotesDatasource(projectDef, ffs, toml)
	}

	private fun writeNoteFile(id: Int, contents: String) {
		val path = datasource.getNotePath(id).toOkioPath()
		ffs.write(path, mustCreate = true) {
			writeUtf8(contents)
		}
	}

	@Test
	fun `Malformed note file is skipped and other notes still load`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		writeNoteFile(99, "this is not valid toml @@@")

		val notes = datasource.loadNotes()

		assertEquals(3, notes.size)
		assertNull(notes.find { it.note.id == 99 })
	}

	@Test
	fun `Note with non-integer id is skipped and other notes still load`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		writeNoteFile(
			98,
			"""
				[note]
				id = "not-an-int"
				created = 0
				content = "broken"
			""".trimIndent()
		)

		val notes = datasource.loadNotes()

		assertEquals(3, notes.size)
		assertNull(notes.find { it.note.id == 98 })
	}
}
