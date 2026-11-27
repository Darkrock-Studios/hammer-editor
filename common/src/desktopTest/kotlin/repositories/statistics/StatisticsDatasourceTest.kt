package repositories.statistics

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatistics
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import getProject1Def
import getProjectDef
import inGroup
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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
	}

	@Test
	fun `Save then load round-trips the statistics`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		val original = stats()

		datasource.saveStatistics(original)

		assertEquals(original, datasource.loadStatistics())
	}

	@Test
	fun `Load ignores statistics left by another project at the same path`() = runTest(mainTestDispatcher) {
		val replaced = getProject1Def()
		createDatasource(replaced).saveStatistics(stats())

		val replacement = replaced.copy(id = ProjectId.randomUUID())
		assertNull(createDatasource(replacement).loadStatistics())
	}

	@Test
	fun `Load ignores statistics in a folder that names no project`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		datasource.saveStatistics(stats())

		val owner = ffs.allPaths.first { it.name == "project-id" }
		ffs.delete(owner)

		assertNull(datasource.loadStatistics())
		assertNull(ProjectStatisticsCacheReader(ffs, toml).loadStatistics(getProject1Def()))
	}

	@Test
	fun `Projects with the same name in different groups keep separate statistics`() =
		runTest(mainTestDispatcher) {
			val topLevel = getProject1Def()
			val inGroup = topLevel.inGroup("Drafts")

			createDatasource(topLevel).saveStatistics(stats().copy(totalWords = 1))
			createDatasource(inGroup).saveStatistics(stats().copy(totalWords = 2))

			assertEquals(1, createDatasource(topLevel).loadStatistics()?.totalWords)
			assertEquals(2, createDatasource(inGroup).loadStatistics()?.totalWords)
		}

	@Test
	fun `Save with a traversal project name lands under the cache root`() =
		runTest(mainTestDispatcher) {
			val datasource = createDatasource(getProjectDef("a/../../../evil"))

			datasource.saveStatistics(stats())

			val cacheRoot = ProjectCachePaths.projectsCacheRoot()
			val written = ffs.allPaths.first { it.name == StatisticsDatasource.FILENAME }
			assertTrue(
				written.isWithin(cacheRoot),
				"Statistics cache escaped the cache root: $written",
			)
		}

	@Test
	fun `Delete removes the cache file`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		datasource.saveStatistics(stats())

		datasource.delete()
		assertNull(datasource.loadStatistics())
		assertTrue(ffs.allPaths.none { it.name == StatisticsDatasource.FILENAME })
	}
}
