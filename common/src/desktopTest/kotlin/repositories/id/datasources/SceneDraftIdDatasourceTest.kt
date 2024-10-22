package repositories.id.datasources

import PROJECT_2_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class SceneDraftIdDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
	}

	private fun createDatasource(projectDef: ProjectDef): SceneDraftIdDatasource {
		val sceneDatasource = SceneDatasource(projectDef, ffs)
		val draftsDatasource = SceneDraftsDatasource(ffs, sceneDatasource)
		return SceneDraftIdDatasource(draftsDatasource)
	}

	@Test
	fun `Find highest ID no entities`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)

		val datasource = createDatasource(getProjectDef(PROJECT_EMPTY_NAME))
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_EMPTY_NAME))

		assertEquals(-1, highestId, "Highest ID should be -1 in empty project")
	}

	@Test
	fun `Find highest ID`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val datasource = createDatasource(getProjectDef(PROJECT_2_NAME))
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_2_NAME))

		assertEquals(11, highestId)
	}
}