package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneDatasourceFilenameTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var ds: SceneDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, PROJECT_1_NAME)
		ds = SceneDatasource(getProjectDef(PROJECT_1_NAME), ffs)
	}

	@Test
	fun `buildArchivedSceneFileName uses tilde delimiter and encodes name`() {
		// `:` is OS-forbidden and must be encoded; the delimiter must be `~`.
		val produced = SceneDatasource.buildArchivedSceneFileName("Chapter 3: Fall", 42)
		assertEquals("Chapter 3꞉ Fall~42.md", produced)
		assertTrue(SceneDatasource.validateArchivedSceneFilename(produced))
	}

	@Test
	fun `archived filename roundtrips through write and read with encoded chars`() {
		val display = "What? (Part II)"
		val id = 7
		val fileName = SceneDatasource.buildArchivedSceneFileName(display, id)

		// Write a fake archived scene with a punctuation-heavy name.
		val archiveDir = ds.getArchivedDirectory().toOkioPath()
		ffs.createDirectories(archiveDir)
		ffs.write(archiveDir.div(fileName)) { writeUtf8("body") }

		val parsed = ds.getArchivedScenes().single { it.id == id }
		assertEquals(display, parsed.name, "decode should restore original display name")
	}

	@Test
	fun `legacy hyphen-delimited active filenames still parse`() {
		// Direct check via the static parser used during reads.
		val legacy = "3-Old Style Name-99.md"
		assertTrue(SceneDatasource.validateSceneFilename(legacy))
		assertEquals(99, SceneDatasource.getSceneIdFromFilename(legacy))
	}

	@Test
	fun `legacy hyphen-delimited archived filenames still parse`() {
		val legacy = "Old Archive Name-7.md"
		assertTrue(SceneDatasource.validateArchivedSceneFilename(legacy))
		assertEquals(7, SceneDatasource.getSceneIdFromFilename(legacy))
	}

	@Test
	fun `new tilde filename parses with id extraction`() {
		val newStyle = "5~Some Name~123.md"
		assertTrue(SceneDatasource.validateSceneFilename(newStyle))
		assertEquals(123, SceneDatasource.getSceneIdFromFilename(newStyle))
	}

	@Test
	fun `new active filename containing previously-banned hyphen parses`() {
		val newStyle = "1~It's-a-me~88.md"
		assertTrue(SceneDatasource.validateSceneFilename(newStyle))
		assertEquals(88, SceneDatasource.getSceneIdFromFilename(newStyle))
	}
}
