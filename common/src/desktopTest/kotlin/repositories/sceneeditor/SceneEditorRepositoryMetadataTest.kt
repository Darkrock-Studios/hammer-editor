package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
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
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class SceneEditorRepositoryMetadataTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@MockK
	private lateinit var syncDataRepository: SyncDataRepository

	@MockK
	private lateinit var idRepository: IdRepository

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource

	@MockK
	private lateinit var statisticsRepository: StatisticsRepository

	private lateinit var referenceIndexDatasource: ReferenceIndexDatasource
	private lateinit var referenceIndexRepository: ReferenceIndexRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		MockKAnnotations.init(this, relaxUnitFun = true)
		mockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
		mockkStatic("org.jetbrains.compose.resources.ResourceEnvironmentKt")
		coEvery {
			getString(any<StringResource>(), any<String>())
		} returns "Mocked"
		every { getSystemResourceEnvironment() } returns mockk(relaxed = true)
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns ProjectMetadata(
			info = Info(
				created = Instant.parse("2022-01-01T00:00:00.000Z"),
				dataVersion = 1,
			)
		)

		setupKoin()
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		unmockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
		mockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
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
		referenceIndexDatasource = ReferenceIndexDatasource(ffs, toml, projectDef)
		referenceIndexRepository = ReferenceIndexRepository(projectDef, referenceIndexDatasource)
		return SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = referenceIndexRepository,
			writingSessionTracker = mockk(relaxed = true),
			clock = Clock.System,
		)
	}

	@Test
	fun `Load Scene Metadata`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val repo = createRepository(projDef)
		val metadata = repo.loadSceneMetadata(sceneId)
		assertEquals("Scene 1 notes", metadata.notes)
	}

	@Test
	fun `Load Scene Metadata with no metadata found`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 7

		val repo = createRepository(projDef)
		val metadata = repo.loadSceneMetadata(sceneId)
		assertEquals(SceneMetadata(currentDraftName = "New Draft"), metadata)
	}

	@Test
	fun `Store Scene Metadata`() = runTest(mainTestDispatcher) {
		val projectMetadata = ProjectMetadata(
			info = Info(
				created = Instant.parse("2022-01-01T00:00:00.000Z"),
				dataVersion = 1,
				serverProjectId = ProjectId("12345"),
			)
		)
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns projectMetadata
		coEvery { syncDataRepository.isServerSynchronized() } returns true
		coEvery { syncDataRepository.isEntityDirty(any()) } returns false
		coEvery { syncDataRepository.markEntityAsDirty(any(), any()) } just Runs

		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val newMetadata = SceneMetadata(
			outline = "Scene 1 outline updates",
			notes = "Scene 1 notes updates",
			currentDraftName = "Scene 1 draft updates"
		)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		repo.storeMetadata(newMetadata, sceneId)

		val loaded = repo.loadSceneMetadata(sceneId)
		assertEquals(newMetadata, loaded)
		coVerify { syncDataRepository.markEntityAsDirty(sceneId, any()) }
	}

	@Test
	fun `Adding confirmed references applies a delta to the index`() = runTest(mainTestDispatcher) {
		coEvery { syncDataRepository.isServerSynchronized() } returns false
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()
		referenceIndexDatasource.saveIndex(ReferenceIndex(isDirty = false))
		referenceIndexRepository.loadIndex()

		val initial = repo.loadSceneMetadata(sceneId)
		repo.storeMetadata(
			initial.copy(confirmedReferences = setOf(7, 9)),
			sceneId,
		)

		val saved = referenceIndexDatasource.loadIndex()
		assertEquals(setOf(sceneId), saved?.entryToScenes?.get(7))
		assertEquals(setOf(sceneId), saved?.entryToScenes?.get(9))
	}

	@Test
	fun `Removing confirmed references applies a delta to the index`() = runTest(mainTestDispatcher) {
		coEvery { syncDataRepository.isServerSynchronized() } returns false
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()
		referenceIndexDatasource.saveIndex(ReferenceIndex(isDirty = false))
		referenceIndexRepository.loadIndex()

		val initial = repo.loadSceneMetadata(sceneId)
		// Seed: scene currently confirmed for entries 7 and 9
		repo.storeMetadata(initial.copy(confirmedReferences = setOf(7, 9)), sceneId)

		repo.storeMetadata(initial.copy(confirmedReferences = setOf(7)), sceneId)

		val saved = referenceIndexDatasource.loadIndex()
		assertEquals(setOf(sceneId), saved?.entryToScenes?.get(7))
		assertEquals(null, saved?.entryToScenes?.get(9))
	}

	@Test
	fun `Deleting a scene removes it from the reference index`() = runTest(mainTestDispatcher) {
		coEvery { syncDataRepository.isServerSynchronized() } returns false
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val sceneId = 1

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()
		referenceIndexDatasource.saveIndex(ReferenceIndex(isDirty = false))
		referenceIndexRepository.loadIndex()

		val initial = repo.loadSceneMetadata(sceneId)
		repo.storeMetadata(initial.copy(confirmedReferences = setOf(7, 9)), sceneId)

		val sceneItem = repo.getSceneItemFromId(sceneId)!!
		repo.deleteScene(sceneItem)

		val saved = referenceIndexDatasource.loadIndex()
		assertEquals(null, saved?.entryToScenes?.get(7))
		assertEquals(null, saved?.entryToScenes?.get(9))
	}

	@Test
	fun `Storing metadata without changing confirmed references does not touch the index`() =
		runTest(mainTestDispatcher) {
			coEvery { syncDataRepository.isServerSynchronized() } returns false
			val projDef = getProject1Def()
			createProject(ffs, PROJECT_1_NAME)
			val sceneId = 1

			val repo = createRepository(projDef)
			repo.initializeSceneEditor()
			referenceIndexDatasource.saveIndex(ReferenceIndex(isDirty = false))
			referenceIndexRepository.loadIndex()

			val initial = repo.loadSceneMetadata(sceneId)
			repo.storeMetadata(initial.copy(confirmedReferences = setOf(7)), sceneId)

			// Same confirmed set; only outline/notes change → no delta should fire
			repo.storeMetadata(
				initial.copy(
					confirmedReferences = setOf(7),
					outline = "different outline",
				),
				sceneId,
			)

			val saved = referenceIndexDatasource.loadIndex()
			assertEquals(setOf(sceneId), saved?.entryToScenes?.get(7))
			assertEquals(false, referenceIndexRepository.isDirty.value)
		}
}