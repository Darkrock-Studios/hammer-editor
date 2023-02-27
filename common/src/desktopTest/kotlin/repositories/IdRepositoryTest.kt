package repositories

import PROJECT_1_NAME
import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import createProject
import getProject1Def
import getProjectsDirectory
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IdRepositoryTest {
	private lateinit var ffs: FakeFileSystem
	private lateinit var idRepository: IdRepository
	private lateinit var toml: Toml
	private lateinit var json: Json

	@Before
	fun setup() {
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()
	}

	@After
	fun tearDown() {
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `findNextId no entities`() {
		createProject(ffs, PROJECT_EMPTY_NAME)

		idRepository = IdRepositoryOkio(getProject1Def(), ffs, json)

		idRepository.findNextId()

		assertEquals(idRepository.claimNextId(), 1, "First claimed ID should be 1 in empty project")
	}

	@Test
	fun `findNextId Scene Ids`() {
		createProject(ffs, PROJECT_1_NAME)

		idRepository = IdRepositoryOkio(getProject1Def(), ffs, json)

		idRepository.findNextId()

		assertEquals(idRepository.claimNextId(), 8, "Failed to find last scene ID")
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

		idRepository = IdRepositoryOkio(projectDef, ffs, json)

		idRepository.findNextId()

		assertEquals(1, idRepository.claimNextId(), "Failed to find last scene ID")
	}
}