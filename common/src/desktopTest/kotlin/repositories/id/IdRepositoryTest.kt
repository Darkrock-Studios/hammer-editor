package repositories.id

import PROJECT_1_NAME
import PROJECT_2_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import createProject
import getProject1Def
import getProjectDef
import getProjectsDirectory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals

class IdRepositoryTest : BaseTest() {
	private lateinit var ffs: FakeFileSystem
	private lateinit var idRepository: IdRepository
	private lateinit var projectSynchronizer: ClientProjectSynchronizer
	private lateinit var toml: Toml

	private lateinit var sceneIdDatasource: SceneIdDatasource
	private lateinit var notesIdDatasource: NotesIdDatasource
	private lateinit var encyclopediaIdDatasource: EncyclopediaIdDatasource
	private lateinit var timeLineEventIdDatasource: TimeLineEventIdDatasource
	private lateinit var sceneDraftIdDatasource: SceneDraftIdDatasource

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		projectSynchronizer = mockk(relaxed = true)

		sceneIdDatasource = SceneIdDatasource(ffs)
		notesIdDatasource = NotesIdDatasource(ffs)
		encyclopediaIdDatasource = EncyclopediaIdDatasource(ffs)
		timeLineEventIdDatasource = TimeLineEventIdDatasource(ffs, toml)
	}

	private fun setupForProject(projectDef: ProjectDef) {
		val sceneDatasource = SceneDatasource(projectDef, ffs)
		sceneDraftIdDatasource = SceneDraftIdDatasource(SceneDraftsDatasource(ffs, sceneDatasource))

		val testModule = module {
			single { projectSynchronizer }
			single { sceneIdDatasource }
			single { notesIdDatasource }
			single { encyclopediaIdDatasource }
			single { timeLineEventIdDatasource }
			single { sceneDraftIdDatasource }
		}

		setupKoin(testModule)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `findNextId no entities`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		setupForProject(getProjectDef(PROJECT_EMPTY_NAME))

		every { projectSynchronizer.isServerSynchronized() } returns false

		idRepository = IdRepository(getProjectDef(PROJECT_EMPTY_NAME))
		idRepository.findNextId()

		assertEquals(idRepository.claimNextId(), 1, "First claimed ID should be 1 in empty project")
	}

	@Test
	fun `findNextId only Scene Ids`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		setupForProject(getProjectDef(PROJECT_1_NAME))

		every { projectSynchronizer.isServerSynchronized() } returns false

		idRepository = IdRepository(getProject1Def())
		idRepository.findNextId()

		assertEquals(8, idRepository.claimNextId(), "Failed to find last scene ID")
	}

	@Test
	fun `findNextId Scene Ids with larger Deleted Id`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		setupForProject(getProjectDef(PROJECT_1_NAME))

		// Last real ID is 7 in "Test Project 1"
		every { projectSynchronizer.isServerSynchronized() } returns true
		coEvery { projectSynchronizer.deletedIds() } returns setOf(8)

		idRepository = IdRepository(getProject1Def())
		idRepository.findNextId()

		assertEquals(9, idRepository.claimNextId(), "Failed to find last entity ID")
	}

	@Test
	fun `No Ids`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		setupForProject(getProjectDef(PROJECT_EMPTY_NAME))

		val projectPath = getProjectsDirectory().div(PROJECT_EMPTY_NAME).toHPath()
		every { projectSynchronizer.isServerSynchronized() } returns false

		val projectDef = ProjectDef(
			name = PROJECT_EMPTY_NAME,
			path = projectPath
		)

		idRepository = IdRepository(projectDef)
		idRepository.findNextId()

		assertEquals(1, idRepository.claimNextId(), "Failed to find last scene ID")
	}

	@Test
	fun `findNextId with all Entity types`() = runTest {
		val projDef = getProjectDef(PROJECT_2_NAME)
		createProject(ffs, PROJECT_2_NAME)
		setupForProject(projDef)

		every { projectSynchronizer.isServerSynchronized() } returns true

		idRepository = IdRepository(projDef)
		idRepository.findNextId()

		assertEquals(24, idRepository.claimNextId(), "Failed to find last entity ID")
	}
}