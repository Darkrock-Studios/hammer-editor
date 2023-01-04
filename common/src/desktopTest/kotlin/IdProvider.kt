import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IdRepositoryTest {
	private lateinit var ffs: FakeFileSystem
	private lateinit var idRepository: IdRepository

	@Before
	fun setup() {
		ffs = FakeFileSystem()
	}

	@After
	fun tearDown() {
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `findLastId Scene Ids`() {
		createProject(ffs, PROJECT_1_NAME)

		idRepository = IdRepositoryOkio(getProject1Def(), ffs)

		idRepository.findNextId()

		assertEquals(idRepository.claimNextSceneId(), 8, "Failed to find last scene ID")
	}

	@Test
	fun `No Ids`() {
		val emptyProjectName = "Empty Project"
		createProject(ffs, emptyProjectName)
		val projectPath = getProjectsDirectory().div(PROJECT_1_NAME).toHPath()

		val projectDef = ProjectDef(
			name = emptyProjectName,
			path = projectPath
		)

		idRepository = IdRepositoryOkio(projectDef, ffs)

		idRepository.findNextId()

		assertEquals(idRepository.claimNextSceneId(), 0, "Failed to find last scene ID")
	}
}