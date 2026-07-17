package repositories.statistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatistics
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsCachePaths
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import getProject1Def
import getProjectDef
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsDatasourceTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
	}

	private fun createDatasource(projectDef: ProjectDef = getProject1Def()) =
		StatisticsDatasource(ffs, toml, projectDef)

	private fun stats() = ProjectStatistics(
		numberOfScenes = 3,
		totalWords = 42,
		wordsByChapter = mapOf(1 to 20, 2 to 22),
		encyclopediaEntriesByType = emptyMap(),
		isDirty = false,
		lastCalculated = Instant.fromEpochSeconds(0),
	)

	@Test
	fun `Load returns null when cache file is missing`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		assertNull(datasource.loadStatistics())
		assertFalse(datasource.exists())
	}

	@Test
	fun `Save then load round-trips the statistics`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		val original = stats()

		datasource.saveStatistics(original)
		assertTrue(datasource.exists())

		val loaded = datasource.loadStatistics()
		assertEquals(original, loaded)
	}

	@Test
	fun `Save with a traversal project name lands under the cache root`() =
		runTest(mainTestDispatcher) {
			val datasource = createDatasource(getProjectDef("a/../../../evil"))

			datasource.saveStatistics(stats())

			val cacheRoot =
				getCacheDirectory().toPath() / StatisticsCachePaths.PROJECTS_DIRECTORY
			val written = ffs.allPaths.first { it.name == StatisticsCachePaths.FILENAME }
			assertTrue(
				written.isWithin(cacheRoot),
				"Statistics cache escaped the cache root: $written",
			)
		}

	@Test
	fun `Delete removes the cache file`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		datasource.saveStatistics(stats())
		assertTrue(datasource.exists())

		datasource.delete()
		assertFalse(datasource.exists())
	}
}
