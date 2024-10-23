package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneEditorRepositoryBufferTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@MockK
	private lateinit var projectSynchronizer: ClientProjectSynchronizer

	@MockK
	private lateinit var idRepository: IdRepository

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		projectMetadataDatasource = ProjectMetadataDatasource(ffs, toml)
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns mockk(relaxed = true)
		coEvery { projectSynchronizer.isServerSynchronized() } returns false
		coEvery { projectSynchronizer.isEntityDirty(any()) } returns false
		coEvery { projectSynchronizer.markEntityAsDirty(any(), any()) } just Runs
	}

	private fun createDatasource(projectDef: ProjectDef): SceneMetadataDatasource {
		return SceneMetadataDatasource(ffs, toml, projectDef)
	}

	private fun createSceneDatasource(projectDef: ProjectDef): SceneDatasource {
		return SceneDatasource(projectDef, ffs)
	}

	private fun createRepository(projectDef: ProjectDef): SceneEditorRepository {
		sceneMetadataDatasource = createDatasource(projectDef)
		sceneDatasource = createSceneDatasource(projectDef)
		return SceneEditorRepositoryOkio(
			projectDef = projectDef,
			projectSynchronizer = projectSynchronizer,
			fileSystem = ffs,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
		)
	}

	@Test
	fun `Subscribe to Buffer Updates`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val newContent = SceneContent(
			scene = SceneItem(
				projectDef = getProject1Def(),
				type = SceneItem.Type.Scene,
				id = 1,
				name = "Scene ID 1",
				order = 0
			),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		val sceneBufferSlot = slot<SceneBuffer>()
		coEvery { onBufferUpdate(capture(sceneBufferSlot)) } just Runs

		val subJob = repo.subscribeToBufferUpdates(null, scope, onBufferUpdate)

		repo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 1) { onBufferUpdate(any()) }

		assertTrue(sceneBufferSlot.isCaptured)
		val updated = sceneBufferSlot.captured
		assertEquals(newContent.scene, updated.content.scene)
		assertEquals(UpdateSource.Editor, updated.source)
		assertEquals(newContent.markdown, updated.content.markdown)
	}

	@Test
	fun `Subscribe to Buffer Updates for one scene`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem2 = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val newContent = SceneContent(
			scene = SceneItem(
				projectDef = getProject1Def(),
				type = SceneItem.Type.Scene,
				id = 1,
				name = "Scene ID 1",
				order = 0
			),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		coEvery { onBufferUpdate(any()) } just Runs

		val subJob = repo.subscribeToBufferUpdates(sceneItem2, scope, onBufferUpdate)
		repo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 0) { onBufferUpdate(any()) }
	}

	@Test
	fun `Store Scene Buffer when no buffer is loaded`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val stored = repo.storeSceneBuffer(sceneItem)
		assertFalse(stored)
	}

	@Test
	fun `Load Scene Buffer, then store it`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val buffer = repo.loadSceneBuffer(sceneItem)
		assertEquals(sceneItem, buffer.content.scene)
		assertEquals("Content of scene id 3", buffer.content.markdown)

		val stored = repo.storeSceneBuffer(sceneItem)
		assertTrue(stored)
	}
}