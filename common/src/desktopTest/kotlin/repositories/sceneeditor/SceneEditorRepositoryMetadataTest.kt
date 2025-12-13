package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
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
		return SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
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
}