package datamigrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration1_2
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration1_2Test : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projDef: ProjectDef

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val projectsRoot = "/projects".toPath()
		val projectPath = projectsRoot.div("Test Project")
		ffs.createDirectories(projectPath)
		projDef = ProjectDef(name = "Test Project", path = projectPath.toHPath())
	}

	@Test
	fun `renames active scene files from hyphen to tilde`() {
		val sceneDir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
		ffs.createDirectories(sceneDir)
		ffs.write(sceneDir.div("0-Scene One-1.md")) { writeUtf8("a") }
		ffs.write(sceneDir.div("1-Scene Two-2.md")) { writeUtf8("b") }

		Migration1_2(ffs).migrate(projDef)

		assertTrue(ffs.exists(sceneDir.div("0~Scene One~1.md")))
		assertTrue(ffs.exists(sceneDir.div("1~Scene Two~2.md")))
		assertFalse(ffs.exists(sceneDir.div("0-Scene One-1.md")))
	}

	@Test
	fun `renames archived scene files`() {
		val archiveDir = SceneDatasource.getArchivedDirectory(projDef, ffs).toOkioPath()
		ffs.createDirectories(archiveDir)
		ffs.write(archiveDir.div("Some Name-7.md")) { writeUtf8("a") }

		Migration1_2(ffs).migrate(projDef)

		assertTrue(ffs.exists(archiveDir.div("Some Name~7.md")))
		assertFalse(ffs.exists(archiveDir.div("Some Name-7.md")))
	}

	@Test
	fun `renames chapter group directories and their child scenes`() {
		val sceneDir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
		val chapterDir = sceneDir.div("1-Chapter One-2")
		ffs.createDirectories(chapterDir)
		ffs.write(chapterDir.div("0-Child A-3.md")) { writeUtf8("a") }
		ffs.write(chapterDir.div("1-Child B-4.md")) { writeUtf8("b") }

		Migration1_2(ffs).migrate(projDef)

		val newChapter = sceneDir.div("1~Chapter One~2")
		assertTrue(ffs.exists(newChapter), "chapter directory should be renamed")
		assertTrue(ffs.exists(newChapter.div("0~Child A~3.md")))
		assertTrue(ffs.exists(newChapter.div("1~Child B~4.md")))
	}

	@Test
	fun `leaves buffer files untouched`() {
		val sceneDir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
		val bufferDir = sceneDir.div(SceneDatasource.BUFFER_DIRECTORY)
		ffs.createDirectories(bufferDir)
		ffs.write(bufferDir.div("42.md")) { writeUtf8("a") }

		Migration1_2(ffs).migrate(projDef)

		assertTrue(ffs.exists(bufferDir.div("42.md")), "buffer file should be untouched")
	}

	@Test
	fun `getSceneFromFilename parses migrated names back to original display`() {
		val sceneDir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
		ffs.createDirectories(sceneDir)
		ffs.write(sceneDir.div("0-Original Name-1.md")) { writeUtf8("a") }

		Migration1_2(ffs).migrate(projDef)

		val ds = SceneDatasource(projDef, ffs)
		val migratedPath = sceneDir.div("0~Original Name~1.md").toHPath()
		val scene = ds.getSceneFromFilename(migratedPath)
		assertEquals("Original Name", scene.name)
		assertEquals(1, scene.id)
		assertEquals(0, scene.order)
	}

	@Test
	fun `is idempotent on already-migrated projects`() {
		val sceneDir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
		ffs.createDirectories(sceneDir)
		ffs.write(sceneDir.div("0~Already New~1.md")) { writeUtf8("a") }

		Migration1_2(ffs).migrate(projDef)

		assertTrue(ffs.exists(sceneDir.div("0~Already New~1.md")))
	}
}
