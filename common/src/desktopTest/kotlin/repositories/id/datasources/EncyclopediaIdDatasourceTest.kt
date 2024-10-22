package repositories.id.datasources

import PROJECT_2_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class EncyclopediaIdDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
	}

	private fun createDatasource(): EncyclopediaIdDatasource {
		return EncyclopediaIdDatasource(ffs)
	}

	@Test
	fun `Find highest ID no entities`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_EMPTY_NAME))

		assertEquals(-1, highestId, "Highest ID should be -1 in empty project")
	}

	@Test
	fun `Find highest ID`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val datasource = createDatasource()
		val highestId = datasource.findHighestId(getProjectDef(PROJECT_2_NAME))

		assertEquals(23, highestId)
	}
}