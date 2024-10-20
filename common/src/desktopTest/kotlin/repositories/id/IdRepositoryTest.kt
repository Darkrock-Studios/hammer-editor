package repositories.id

import PROJECT_1_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import createProject
import getProject1Def
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

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		projectSynchronizer = mockk(relaxed = true)

		val testModule = module {
			single { projectSynchronizer }
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
		every { projectSynchronizer.isServerSynchronized() } returns false

		idRepository = IdRepositoryOkio(getProject1Def(), ffs, toml)

		idRepository.findNextId()

		assertEquals(idRepository.claimNextId(), 1, "First claimed ID should be 1 in empty project")
	}

	@Test
	fun `findNextId Scene Ids`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		every { projectSynchronizer.isServerSynchronized() } returns false

		idRepository = IdRepositoryOkio(getProject1Def(), ffs, toml)

		idRepository.findNextId()

		assertEquals(8, idRepository.claimNextId(), "Failed to find last scene ID")
	}

	@Test
	fun `findNextId Scene Ids - With larger Deleted Id`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		// Last real ID is 7 in "Test Project 1"
		every { projectSynchronizer.isServerSynchronized() } returns true
		coEvery { projectSynchronizer.deletedIds() } returns setOf(8)

		idRepository = IdRepositoryOkio(getProject1Def(), ffs, toml)

		idRepository.findNextId()

		assertEquals(9, idRepository.claimNextId(), "Failed to find last entity ID")
	}

	@Test
	fun `No Ids`() = runTest {
		val emptyProjectName = "Empty Project"
		createProject(ffs, emptyProjectName)
		val projectPath = getProjectsDirectory().div(PROJECT_1_NAME).toHPath()
		every { projectSynchronizer.isServerSynchronized() } returns false

		val projectDef = ProjectDef(
			name = emptyProjectName,
			path = projectPath
		)

		idRepository = IdRepositoryOkio(projectDef, ffs, toml)

		idRepository.findNextId()

		assertEquals(1, idRepository.claimNextId(), "Failed to find last scene ID")
	}
}