package repositories.id

import PROJECT_1_NAME
import PROJECT_2_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.*
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
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

class IdAllocatorTest : BaseTest() {
	private lateinit var ffs: FakeFileSystem
	private lateinit var idAllocator: IdAllocator
	private lateinit var syncDataRepository: SyncDataRepository
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
		syncDataRepository = mockk(relaxed = true)

		sceneIdDatasource = SceneIdDatasource(ffs)
		notesIdDatasource = NotesIdDatasource(ffs)
		encyclopediaIdDatasource = EncyclopediaIdDatasource(ffs)
		timeLineEventIdDatasource = TimeLineEventIdDatasource(ffs, toml)
	}

	private fun setupForProject(projectDef: ProjectDef) {
		val sceneDatasource = SceneDatasource(projectDef, ffs)
		sceneDraftIdDatasource = SceneDraftIdDatasource(SceneDraftsDatasource(ffs, sceneDatasource))

		val testModule = module {
			single { syncDataRepository }
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

		every { syncDataRepository.isServerSynchronized() } returns false

		idAllocator = IdAllocator(getProjectDef(PROJECT_EMPTY_NAME))
		idAllocator.findNextId()

		assertEquals(idAllocator.claimNextId(), 1, "First claimed ID should be 1 in empty project")
	}

	@Test
	fun `findNextId only Scene Ids`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		setupForProject(getProjectDef(PROJECT_1_NAME))

		every { syncDataRepository.isServerSynchronized() } returns false

		idAllocator = IdAllocator(getProject1Def())
		idAllocator.findNextId()

		assertEquals(8, idAllocator.claimNextId(), "Failed to find last scene ID")
	}

	@Test
	fun `findNextId Scene Ids with larger Deleted Id`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		setupForProject(getProjectDef(PROJECT_1_NAME))

		// Last real ID is 7 in "Test Project 1"
		every { syncDataRepository.isServerSynchronized() } returns true
		coEvery { syncDataRepository.deletedIds() } returns setOf(8)

		idAllocator = IdAllocator(getProject1Def())
		idAllocator.findNextId()

		assertEquals(9, idAllocator.claimNextId(), "Failed to find last entity ID")
	}

	@Test
	fun `No Ids`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		setupForProject(getProjectDef(PROJECT_EMPTY_NAME))

		val projectPath = getProjectsDirectory().div(PROJECT_EMPTY_NAME).toHPath()
		every { syncDataRepository.isServerSynchronized() } returns false

		val projectDef = ProjectDef(
			name = PROJECT_EMPTY_NAME,
			path = projectPath
		)

		idAllocator = IdAllocator(projectDef)
		idAllocator.findNextId()

		assertEquals(1, idAllocator.claimNextId(), "Failed to find last scene ID")
	}

	@Test
	fun `findNextId with all Entity types`() = runTest {
		val projDef = getProjectDef(PROJECT_2_NAME)
		createProject(ffs, PROJECT_2_NAME)
		setupForProject(projDef)

		every { syncDataRepository.isServerSynchronized() } returns true

		idAllocator = IdAllocator(projDef)
		idAllocator.findNextId()

		assertEquals(24, idAllocator.claimNextId(), "Failed to find last entity ID")
	}
}