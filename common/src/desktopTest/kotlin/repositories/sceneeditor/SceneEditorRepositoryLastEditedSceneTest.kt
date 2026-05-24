package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestClock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SceneEditorRepositoryLastEditedSceneTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource
	private lateinit var clock: TestClock

	@MockK
	private lateinit var syncDataRepository: SyncDataRepository

	@MockK
	private lateinit var idRepository: IdRepository

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var statisticsRepository: StatisticsRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		clock = TestClock(Clock.System)
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		statisticsRepository = mockk(relaxed = true)
		projectMetadataDatasource = ProjectMetadataDatasource(ffs, toml)

		coEvery { syncDataRepository.isServerSynchronized() } returns false
		coEvery { syncDataRepository.isEntityDirty(any()) } returns false
		coEvery { syncDataRepository.markEntityAsDirty(any(), any()) } just Runs
	}

	private fun createRepository(projectDef: ProjectDef): SceneEditorRepository {
		sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		sceneDatasource = SceneDatasource(projectDef, ffs)
		return SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = mockk(relaxed = true),
			writingSessionTracker = mockk(relaxed = true),
			clock = clock,
			strRes = mockk(relaxed = true),
		)
	}

	private suspend fun lastEdited(sceneId: Int): kotlin.time.Instant? =
		sceneMetadataDatasource.loadMetadata(sceneId)?.lastEdited

	@Test
	fun `storeSceneBuffer stamps scene's lastEdited`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = projDef,
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0,
		)
		val content = SceneContent(scene = sceneItem, markdown = "edited")
		repo.onContentChanged(content, UpdateSource.Editor)
		advanceUntilIdle()

		repo.storeSceneBuffer(sceneItem)
		advanceUntilIdle()

		assertEquals(clock.now(), lastEdited(3))
	}

	@Test
	fun `storeSceneBuffer with sync source leaves lastEdited untouched`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = projDef,
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0,
		)
		val content = SceneContent(scene = sceneItem, markdown = "synced in")
		repo.onContentChanged(content, UpdateSource.Sync)
		advanceUntilIdle()

		repo.storeSceneBuffer(sceneItem)
		advanceUntilIdle()

		assertNull(lastEdited(3))
	}

	@Test
	fun `editing two scenes in sequence stamps each scene's own lastEdited`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val first = SceneItem(projDef, SceneItem.Type.Scene, 1, "Scene 1", 0)
		val second = SceneItem(projDef, SceneItem.Type.Scene, 3, "Scene 3", 0)

		repo.onContentChanged(SceneContent(first, "a"), UpdateSource.Editor)
		advanceUntilIdle()
		repo.storeSceneBuffer(first)
		advanceUntilIdle()
		val firstStamp = lastEdited(1)
		assertNotNull(firstStamp)

		clock.advanceTime(1.minutes)

		repo.onContentChanged(SceneContent(second, "b"), UpdateSource.Editor)
		advanceUntilIdle()
		repo.storeSceneBuffer(second)
		advanceUntilIdle()
		val secondStamp = lastEdited(3)
		assertNotNull(secondStamp)

		assertTrue(secondStamp > firstStamp, "later edit must produce a later lastEdited")
		// First scene's stamp wasn't overwritten by editing the second.
		assertEquals(firstStamp, lastEdited(1))
	}

	@Test
	fun `recording activity on a scene with no metadata backfills created from project createdDate`() =
		runTest(mainTestDispatcher) {
			val projDef = getProject1Def()
			createProject(ffs, PROJECT_1_NAME)

			val projectCreated = projectMetadataDatasource.loadMetadata(projDef).info.created
			clock.advanceTime(30.minutes)

			val repo = createRepository(projDef)
			repo.initializeSceneEditor()

			val sceneItem = SceneItem(projDef, SceneItem.Type.Scene, 7, "Scene 7", 0)
			repo.onContentChanged(SceneContent(sceneItem, "first edit"), UpdateSource.Editor)
			advanceUntilIdle()
			repo.storeSceneBuffer(sceneItem)
			advanceUntilIdle()

			val metadata = sceneMetadataDatasource.loadMetadata(7)
			assertNotNull(metadata)
			assertEquals(projectCreated, metadata.created)
			assertEquals(clock.now(), metadata.lastEdited)
		}

	@Test
	fun `recording activity on a scene with existing metadata preserves its created`() =
		runTest(mainTestDispatcher) {
			val projDef = getProject1Def()
			createProject(ffs, PROJECT_1_NAME)

			val repo = createRepository(projDef)
			val originalCreated = clock.now()
			sceneMetadataDatasource.storeMetadata(
				SceneMetadata(created = originalCreated, lastEdited = originalCreated),
				sceneId = 5,
			)

			clock.advanceTime(2.minutes)

			repo.initializeSceneEditor()

			val sceneItem = SceneItem(projDef, SceneItem.Type.Scene, 5, "Scene 5", 0)
			repo.onContentChanged(SceneContent(sceneItem, "edit"), UpdateSource.Editor)
			advanceUntilIdle()
			repo.storeSceneBuffer(sceneItem)
			advanceUntilIdle()

			val metadata = sceneMetadataDatasource.loadMetadata(5)
			assertNotNull(metadata)
			assertEquals(originalCreated, metadata.created, "created must not be rewritten when already set")
			assertEquals(clock.now(), metadata.lastEdited)
		}

	@Test
	fun `reIdScene carries lastEdited to the new scene id`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(projDef, SceneItem.Type.Scene, 3, "Scene 3", 0)
		repo.onContentChanged(SceneContent(sceneItem, "edited"), UpdateSource.Editor)
		advanceUntilIdle()
		repo.storeSceneBuffer(sceneItem)
		advanceUntilIdle()
		val originalStamp = lastEdited(3)
		assertNotNull(originalStamp)

		repo.reIdScene(oldId = 3, newId = 99)

		assertNull(lastEdited(3), "old metadata file should have been moved")
		assertEquals(originalStamp, lastEdited(99))
	}
}
