package repositories.id.datasources

import PROJECT_2_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class SceneIdDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
	}

	private fun createDatasource(): SceneIdDatasource {
		return SceneIdDatasource(ffs)
	}

	@Test
	fun `Find highest ID no entities`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_EMPTY_NAME))

		assertEquals(-1, highestId, "Highest ID should be -1 in empty project")
	}

	@Test
	fun `Find highest ID`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_2_NAME))

		assertEquals(8, highestId)
	}

	@Test
	fun `Find highest ID includes archived scenes`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val projectDef = getProjectDef(PROJECT_2_NAME)
		val scenesDir = projectDef.path.toOkioPath() / SceneDatasource.SCENE_DIRECTORY
		val archivedDir = scenesDir / SceneDatasource.ARCHIVED_DIRECTORY

		// Create archived directory and add a scene with higher ID than existing scenes
		// Filename format: {order}-{name}-{id}.md
		ffs.createDirectories(archivedDir)
		val archivedSceneFile = archivedDir / "0-Archived Scene-99.md"
		ffs.write(archivedSceneFile) {
			writeUtf8("# Archived Scene Content")
		}

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(projectDef)

		assertEquals(99, highestId, "Highest ID should include archived scenes")
	}

	@Test
	fun `Find highest ID with only archived scenes`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)

		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		val scenesDir = projectDef.path.toOkioPath() / SceneDatasource.SCENE_DIRECTORY
		val archivedDir = scenesDir / SceneDatasource.ARCHIVED_DIRECTORY

		// Create archived directory with a scene
		// Filename format: {order}-{name}-{id}.md
		ffs.createDirectories(archivedDir)
		val archivedSceneFile = archivedDir / "0-Archived Scene-5.md"
		ffs.write(archivedSceneFile) {
			writeUtf8("# Archived Scene Content")
		}

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(projectDef)

		assertEquals(5, highestId, "Highest ID should be found in archived directory")
	}
}