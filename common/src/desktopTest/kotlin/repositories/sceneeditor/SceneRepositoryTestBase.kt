package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import createProject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import utils.BaseTest
import kotlin.time.Clock

/**
 * Boots a real [SceneRepository] against Project 1 on a fake filesystem.
 *
 * Project 1 layout (id == global index):
 * Root(0) -> [Scene 1, Group 2 -> [Scene 3, Scene 4, Scene 5], Scene 6, Scene 7]
 */
abstract class SceneRepositoryTestBase : BaseTest() {

	protected lateinit var ffs: FakeFileSystem
	protected lateinit var projectPath: HPath
	protected lateinit var projectsRepo: ProjectsRepository
	protected lateinit var syncJournal: SyncJournal
	protected lateinit var projectDef: ProjectDef
	protected lateinit var repo: SceneRepository
	protected lateinit var idAllocator: IdAllocator
	protected lateinit var metadataDatasource: SceneMetadataDatasource
	protected lateinit var sceneDatasource: SceneDatasource
	protected var nextId = 8

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		syncJournal = mockk()
		every { syncJournal.isServerSynchronized() } returns false

		metadataDatasource = mockk(relaxed = true)

		projectsRepo = mockk()
		every { projectsRepo.getProjectsDirectory() } returns
			rootDir.toPath().div(PROJ_DIR).toHPath()

		projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(PROJECT_1_NAME).toHPath()

		projectDef = ProjectDef(
			name = PROJECT_1_NAME,
			path = projectPath
		)
		sceneDatasource = SceneDatasource(projectDef, ffs)

		nextId = 8
		idAllocator = mockk()
		coEvery { idAllocator.claimNextId() } answers { nextId++ }
		coEvery { idAllocator.findNextId() } answers {}

		createProject(ffs, PROJECT_1_NAME)

		setupKoin()

		repo = createRepository()

		runBlocking {
			repo.initializeSceneEditor()
		}
	}

	protected fun createRepository(): SceneRepository = SceneRepository(
		projectDef = projectDef,
		syncJournal = syncJournal,
		idAllocator = idAllocator,
		sceneMetadataDatasource = metadataDatasource,
		sceneDatasource = sceneDatasource,
		clock = Clock.System,
	)

	companion object {
		const val PROJ_DIR = "HammerProjects"
	}
}
