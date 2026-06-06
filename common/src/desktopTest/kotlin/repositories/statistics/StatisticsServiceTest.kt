package repositories.statistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatistics
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Instant

class StatisticsServiceTest : BaseTest() {

	private val projectDef = ProjectDef("Test", HPath("/projects/Test", "Test", false))

	private lateinit var statisticsRepository: StatisticsRepository
	private lateinit var sceneEditorRepository: SceneRepository
	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var encyclopediaRepository: EncyclopediaRepository
	private lateinit var notesRepository: NotesRepository
	private lateinit var timeLineRepository: TimeLineRepository
	private lateinit var writingActivityRepository: WritingActivityRepository
	private lateinit var referenceIndexService: ReferenceIndexService
	private lateinit var projectDataRepository: ProjectDataRepository

	private fun sceneItem(id: Int, type: SceneItem.Type) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = "S$id", order = id)

	private fun rootOnlyTree(): ImmutableTree<SceneItem> {
		val root = TreeValue(
			value = sceneItem(0, SceneItem.Type.Root),
			index = 0, parent = -1, children = emptyList(), depth = 0, totalChildren = 0,
		)
		return ImmutableTree(root = root, totalChildren = 0)
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(module {})

		statisticsRepository = mockk(relaxed = true)
		sceneEditorRepository = mockk(relaxed = true)
		sceneMetadataDatasource = mockk(relaxed = true)
		encyclopediaRepository = mockk(relaxed = true)
		notesRepository = mockk(relaxed = true)
		timeLineRepository = mockk(relaxed = true)
		writingActivityRepository = mockk(relaxed = true)
		referenceIndexService = mockk(relaxed = true)
		projectDataRepository = mockk(relaxed = true)

		// A complete but empty recalculation pipeline so recalc paths can run to completion.
		every { sceneEditorRepository.sceneTreeUpdates } returns MutableStateFlow(rootOnlyTree())
		every { encyclopediaRepository.entryListFlow } returns MutableStateFlow(emptyList())
		every { notesRepository.notesListFlow } returns MutableStateFlow(emptyList())
		coEvery { timeLineRepository.loadTimeline() } returns TimeLineContainer(emptyList())
		coEvery { writingActivityRepository.loadAllLogs() } returns emptyMap()
		coEvery { referenceIndexService.loadIndex() } returns ReferenceIndex()
		coEvery { projectDataRepository.load() } returns StoredProjectData()
		coEvery { sceneMetadataDatasource.loadMetadata(any()) } returns null
	}

	private fun makeService() = StatisticsService(
		projectDef = projectDef,
		statisticsRepository = statisticsRepository,
		sceneEditorRepository = sceneEditorRepository,
		sceneMetadataDatasource = sceneMetadataDatasource,
		encyclopediaRepository = encyclopediaRepository,
		notesRepository = notesRepository,
		timeLineRepository = timeLineRepository,
		writingActivityRepository = writingActivityRepository,
		referenceIndexService = referenceIndexService,
		projectDataRepository = projectDataRepository,
		clock = Clock.System,
	)

	private fun cachedStats(schema: Int, dirty: Boolean) = ProjectStatistics(
		numberOfScenes = 0,
		totalWords = 0,
		wordsByChapter = emptyMap(),
		encyclopediaEntriesByType = emptyMap(),
		isDirty = dirty,
		lastCalculated = Instant.fromEpochSeconds(0),
		schemaVersion = schema,
	)

	@Test
	fun `loadStatistics returns the cache when it is current and clean`() = runTest(mainTestDispatcher) {
		val cached = cachedStats(schema = ProjectStatistics.CURRENT_SCHEMA_VERSION, dirty = false)
		coEvery { statisticsRepository.loadStatistics() } returns cached

		val result = makeService().loadStatistics()

		assertSame(cached, result)
		coVerify(exactly = 0) { statisticsRepository.saveStatistics(any()) }
	}

	@Test
	fun `loadStatistics recalculates when the cache is missing`() = runTest(mainTestDispatcher) {
		coEvery { statisticsRepository.loadStatistics() } returns null

		makeService().loadStatistics()

		coVerify(exactly = 1) { statisticsRepository.saveStatistics(any()) }
	}

	@Test
	fun `loadStatistics recalculates when the cache schema is outdated`() = runTest(mainTestDispatcher) {
		coEvery { statisticsRepository.loadStatistics() } returns
			cachedStats(schema = ProjectStatistics.CURRENT_SCHEMA_VERSION - 1, dirty = false)

		makeService().loadStatistics()

		coVerify(exactly = 1) { statisticsRepository.saveStatistics(any()) }
	}

	@Test
	fun `loadStatistics recalculates when the cache is dirty`() = runTest(mainTestDispatcher) {
		coEvery { statisticsRepository.loadStatistics() } returns
			cachedStats(schema = ProjectStatistics.CURRENT_SCHEMA_VERSION, dirty = true)

		makeService().loadStatistics()

		coVerify(exactly = 1) { statisticsRepository.saveStatistics(any()) }
	}

	@Test
	fun `recalculateStatistics aggregates scene words chapters and the longest scene`() =
		runTest(mainTestDispatcher) {
			// root -> chapter -> [sceneA(3 words), sceneB(2 words)]
			val sceneA = TreeValue(
				value = sceneItem(2, SceneItem.Type.Scene),
				index = 2, parent = 1, children = emptyList(), depth = 2, totalChildren = 0,
			)
			val sceneB = TreeValue(
				value = sceneItem(3, SceneItem.Type.Scene),
				index = 3, parent = 1, children = emptyList(), depth = 2, totalChildren = 0,
			)
			val chapter = TreeValue(
				value = sceneItem(1, SceneItem.Type.Group),
				index = 1, parent = 0, children = listOf(sceneA, sceneB), depth = 1, totalChildren = 2,
			)
			val root = TreeValue(
				value = sceneItem(0, SceneItem.Type.Root),
				index = 0, parent = -1, children = listOf(chapter), depth = 0, totalChildren = 3,
			)
			every { sceneEditorRepository.sceneTreeUpdates } returns
				MutableStateFlow(ImmutableTree(root = root, totalChildren = 3))
			every { sceneEditorRepository.loadSceneMarkdownRaw(any(), any()) } answers {
				when (firstArg<SceneItem>().id) {
					2 -> "one two three"
					3 -> "four five"
					else -> ""
				}
			}
			coEvery { statisticsRepository.loadStatistics() } returns null

			val saved = slot<ProjectStatistics>()
			coEvery { statisticsRepository.saveStatistics(capture(saved)) } returns Unit

			val service = makeService()
			val stats = service.recalculateStatistics()

			assertEquals(2, stats.numberOfScenes)
			assertEquals(5, stats.totalWords)
			assertEquals(mapOf(1 to 5), stats.wordsByChapter)
			assertEquals(2, stats.longestSceneId)
			assertEquals(3, stats.longestSceneWords)
			assertEquals(2, stats.shortestSceneWords)
			assertEquals(2, stats.medianSceneWords)
			assertEquals(ProjectStatistics.CURRENT_SCHEMA_VERSION, stats.schemaVersion)
			assertFalse(stats.isDirty)
			// The computed stats are persisted.
			assertEquals(stats, saved.captured)
			// Calculation flag is reset once finished.
			assertFalse(service.isCalculating.value)
		}
}
