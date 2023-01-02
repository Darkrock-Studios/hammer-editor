import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProvider
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProviderOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IdProviderTest {
	private lateinit var ffs: FakeFileSystem
	private lateinit var isProvider: IdProvider

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

		isProvider = IdProviderOkio(getProject1Def(), ffs)

		isProvider.findNextId()

		assertEquals(isProvider.claimNextSceneId(), 8, "Failed to find last scene ID")
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

		isProvider = IdProviderOkio(projectDef, ffs)

		isProvider.findNextId()

		assertEquals(isProvider.claimNextSceneId(), 0, "Failed to find last scene ID")
	}
}