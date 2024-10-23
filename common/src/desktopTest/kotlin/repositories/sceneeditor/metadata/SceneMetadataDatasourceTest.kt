package repositories.sceneeditor.metadata

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SceneMetadataDatasourceTest : BaseTest() {
	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
	}

	private fun createDatasource(projectDef: ProjectDef): SceneMetadataDatasource {
		return SceneMetadataDatasource(ffs, toml, projectDef)
	}

	@Test
	fun `Load Metadata`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val datasource = createDatasource(projDef)
		val metadata = datasource.loadMetadata(1)
		assertEquals(
			SceneMetadata(
				outline = "Scene 1 outline",
				notes = "Scene 1 notes",
			),
			metadata
		)
	}

	@Test
	fun `Load Metadata with empty content`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val datasource = createDatasource(projDef)
		val metadata = datasource.loadMetadata(3)
		assertEquals(
			SceneMetadata(
				outline = "",
				notes = "",
			),
			metadata
		)
	}

	@Test
	fun `Load Metadata that doesn't exist`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val datasource = createDatasource(projDef)
		val metadata = datasource.loadMetadata(2)
		assertNull(metadata)
	}

	@Test
	fun `Store Metadata that doesn't exist yet`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 2

		val metadata = SceneMetadata(
			outline = "Scene 2 outline",
			notes = "Scene 2 notes",
		)

		val datasource = createDatasource(projDef)

		val file = datasource.getMetadataPath(sceneId).toOkioPath()
		assertFalse(ffs.exists(file))

		datasource.storeMetadata(metadata, sceneId)

		assertTrue(ffs.exists(file))
		val loadedMetadata: SceneMetadata = ffs.readToml(file, toml)
		assertEquals(metadata, loadedMetadata)
	}

	@Test
	fun `Overwrite Metadata`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val metadata = SceneMetadata(
			outline = "Scene 1 outline updated",
			notes = "Scene 1 notes updated",
		)

		val datasource = createDatasource(projDef)

		val file = datasource.getMetadataPath(sceneId).toOkioPath()
		assertTrue(ffs.exists(file))

		datasource.storeMetadata(metadata, sceneId)

		assertTrue(ffs.exists(file))
		val loadedMetadata: SceneMetadata = ffs.readToml(file, toml)
		assertEquals(metadata, loadedMetadata)
	}

	@Test
	fun `ReId Scene Metadata`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val oldId = 4
		val newId = 5

		val datasource = createDatasource(projDef)

		val oldFile = datasource.getMetadataPath(oldId).toOkioPath()
		val newFile = datasource.getMetadataPath(newId).toOkioPath()

		assertTrue(ffs.exists(oldFile))
		assertFalse(ffs.exists(newFile))

		val oldData = datasource.loadMetadata(oldId)

		datasource.reIdSceneMetadata(oldId = oldId, newId = newId)

		assertFalse(ffs.exists(oldFile))
		assertTrue(ffs.exists(newFile))

		val newData = datasource.loadMetadata(newId)
		assertEquals(oldData, newData)
	}
}