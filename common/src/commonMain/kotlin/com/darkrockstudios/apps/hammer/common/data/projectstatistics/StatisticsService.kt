package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Service responsible for calculating project statistics.
 * Uses StatisticsRepository for persistence.
 */
class StatisticsService(
	projectDef: ProjectDef,
	private val statisticsRepository: StatisticsRepository,
	private val sceneEditorRepository: SceneRepository,
	private val sceneMetadataDatasource: SceneMetadataDatasource,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val notesRepository: NotesRepository,
	private val timeLineRepository: TimeLineRepository,
	private val writingActivityRepository: WritingActivityRepository,
	private val referenceIndexService: ReferenceIndexService,
	private val projectDataRepository: ProjectDataRepository,
	private val clock: Clock,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val serviceScope = CoroutineScope(dispatcherDefault)

	// Delegate to repository's flows
	val statsFlow: SharedFlow<ProjectStatistics> = statisticsRepository.statsFlow
	val isDirty: StateFlow<Boolean> = statisticsRepository.isDirty

	private val _isCalculating = MutableStateFlow(false)
	val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

	init {
		projectScope.scope.registerCallback(this)
	}

	/**
	 * Load statistics from cache if available and not dirty.
	 * If cache is missing, dirty, or has an outdated schema, calculates fresh statistics.
	 */
	suspend fun loadStatistics(): ProjectStatistics {
		val cached = statisticsRepository.loadStatistics()
		val isCurrentSchema = cached?.schemaVersion == ProjectStatistics.CURRENT_SCHEMA_VERSION
		return if (cached != null && isCurrentSchema && !cached.isDirty) {
			Napier.d("Loaded statistics from cache")
			cached
		} else {
			when {
				cached == null -> Napier.d("Cache missing, calculating statistics")
				!isCurrentSchema -> Napier.i(
					"Statistics cache schema is outdated (was ${cached.schemaVersion}, " +
						"expected ${ProjectStatistics.CURRENT_SCHEMA_VERSION}); recalculating"
				)

				else -> Napier.d("Cache is dirty, will recalculate")
			}
			recalculateStatistics()
		}
	}

	/**
	 * Force recalculation of all statistics, updating the cache.
	 */
	suspend fun recalculateStatistics(): ProjectStatistics {
		_isCalculating.value = true
		val startTime = clock.now()
		Napier.d("Recalculating project statistics...")

		try {
			val tree = sceneEditorRepository.sceneTreeUpdates.first().root

			var numScenes = 0
			var totalWords = 0
			var longestSceneId: Int? = null
			var longestSceneName: String? = null
			var longestSceneWords = 0
			var lastEditedSceneId: Int? = null
			var lastEditedSceneName: String? = null
			var lastEditedAt: Instant? = null
			val wordsByScene = mutableMapOf<Int, Int>()

			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = sceneEditorRepository.countWordsInScene(node.value)
					wordsByScene[node.value.id] = count
					totalWords += count
					numScenes++
					if (count > longestSceneWords) {
						longestSceneWords = count
						longestSceneId = node.value.id
						longestSceneName = node.value.name
					}
					val edited = sceneMetadataDatasource.loadMetadata(node.value.id)?.lastEdited
					val currentMax = lastEditedAt
					if (edited != null && (currentMax == null || edited > currentMax)) {
						lastEditedAt = edited
						lastEditedSceneId = node.value.id
						lastEditedSceneName = node.value.name
					}
				}
			}

			val sceneWordCounts = wordsByScene.values.toList()
			val shortestSceneWords = sceneWordCounts.minOrNull() ?: 0
			val medianSceneWords = median(sceneWordCounts)
			val sceneWordsStdDev = stdDev(sceneWordCounts)

			yield()

			val wordsByChapter = mutableMapOf<Int, Int>()
			tree.children.forEach { node ->
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						wordsInChapter += wordsByScene[child.value.id] ?: 0
					}
				}
				wordsByChapter[node.value.id] = wordsInChapter
			}

			yield()

			encyclopediaRepository.loadEntries()
			val entries = encyclopediaRepository.entryListFlow.first()
			val entriesByType = mutableMapOf<String, Int>()
			entries.forEach { entry ->
				val typeKey = entry.type.name
				entriesByType[typeKey] = (entriesByType[typeKey] ?: 0) + 1
			}

			yield()

			val notesCount = notesRepository.notesListFlow.first().size
			val timelineCount = timeLineRepository.loadTimeline().events.size

			yield()

			val timeZone = TimeZone.currentSystemDefault()
			val dailyWordTotals = mutableMapOf<String, Int>()
			val wordsPerDevice = mutableMapOf<String, Int>()
			writingActivityRepository.loadAllLogs().values.forEach { deviceLog ->
				var deviceTotal = 0
				deviceLog.sessions.forEach { session ->
					// Count every session with words, sealed or not.
					if (session.wordsWritten <= 0) return@forEach
					val date = session.startedAt.toLocalDateTime(timeZone).date.toString()
					dailyWordTotals[date] = (dailyWordTotals[date] ?: 0) + session.wordsWritten
					deviceTotal += session.wordsWritten
				}
				if (deviceTotal > 0) {
					wordsPerDevice[deviceLog.deviceLabel] =
						(wordsPerDevice[deviceLog.deviceLabel] ?: 0) + deviceTotal
				}
			}

			yield()

			val referenceIndex = referenceIndexService.loadIndex()
			var totalEntryConnections = 0
			val appearances = entries.map { entry ->
				val sceneCount = referenceIndex.entryToScenes[entry.id]?.size ?: 0
				totalEntryConnections += sceneCount
				EntryAppearance(
					entryId = entry.id,
					name = entry.name,
					type = entry.type,
					sceneCount = sceneCount,
				)
			}
			val topAppearances = appearances
				.filter { it.sceneCount > 0 }
				.sortedByDescending { it.sceneCount }
				.take(TOP_APPEARANCES_LIMIT)

			yield()

			val wordCountGoal = projectDataRepository.load().data.wordCountGoal

			val stats = ProjectStatistics(
				numberOfScenes = numScenes,
				totalWords = totalWords,
				wordsByChapter = wordsByChapter,
				encyclopediaEntriesByType = entriesByType,
				longestSceneId = longestSceneId,
				longestSceneName = longestSceneName,
				longestSceneWords = longestSceneWords,
				lastEditedSceneId = lastEditedSceneId,
				lastEditedSceneName = lastEditedSceneName,
				lastEditedAt = lastEditedAt,
				shortestSceneWords = shortestSceneWords,
				medianSceneWords = medianSceneWords,
				sceneWordsStdDev = sceneWordsStdDev,
				numberOfNotes = notesCount,
				numberOfTimelineEvents = timelineCount,
				dailyWordTotals = dailyWordTotals,
				wordsPerDevice = wordsPerDevice,
				topAppearances = topAppearances,
				totalEntryConnections = totalEntryConnections,
				wordCountGoal = wordCountGoal,
				isDirty = false,
				lastCalculated = clock.now(),
				schemaVersion = ProjectStatistics.CURRENT_SCHEMA_VERSION,
			)

			statisticsRepository.saveStatistics(stats)

			val duration = clock.now() - startTime
			Napier.i("Statistics calculated in ${duration.inWholeMilliseconds}ms: $numScenes scenes, $totalWords words")
			return stats
		} finally {
			_isCalculating.value = false
		}
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("StatisticsService Closed")
	}

	companion object {
		const val TOP_APPEARANCES_LIMIT = 10

		private fun median(counts: List<Int>): Int {
			if (counts.isEmpty()) return 0
			val sorted = counts.sorted()
			val mid = sorted.size / 2
			return if (sorted.size % 2 == 1) {
				sorted[mid]
			} else {
				(sorted[mid - 1] + sorted[mid]) / 2
			}
		}

		private fun stdDev(counts: List<Int>): Int {
			if (counts.size < 2) return 0
			val mean = counts.average()
			val variance = counts.sumOf { val d = it - mean; d * d } / counts.size
			return sqrt(variance).toInt()
		}
	}
}

/**
 * Extension function to count words in a scene.
 */
fun SceneRepository.countWordsInScene(sceneItem: SceneItem): Int {
	return countWords(loadSceneMarkdownRaw(sceneItem))
}
