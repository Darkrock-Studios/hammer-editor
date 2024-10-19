package repositories.scenedraft

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProjectDef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class SceneDraftRepositoryTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	private lateinit var sceneEditorRepository: SceneEditorRepository
	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneEditorRepository = mockk<SceneEditorRepositoryOkio>()
		every { sceneEditorRepository.getSceneDirectory() } answers {
			SceneEditorRepositoryOkio.getSceneDirectory(projectDef, ffs)
		}

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
	}

	private fun createRepository(): SceneDraftRepository {
		return SceneDraftRepositoryOkio(projectDef, sceneEditorRepository, ffs)
	}

	@Test
	fun `Initialize scene draft repository`() {
		val repo = createRepository()
	}

	@Test
	fun `Get All Drafts`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		val drafts = repo.getAllDrafts()

		assertEquals(3, drafts.size)
	}
}