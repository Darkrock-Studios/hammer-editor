package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext
import kotlin.math.sqrt
import kotlin.time.Clock

/**
 * Service responsible for calculating project statistics.
 * Uses StatisticsRepository for persistence.
 */
class StatisticsService(
	projectDef: ProjectDef,
	private val statisticsRepository: StatisticsRepository,
	private val sceneEditorRepository: SceneEditorRepository,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val notesRepository: NotesRepository,
	private val timeLineRepository: TimeLineRepository,
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
			val sceneSummary = sceneEditorRepository.sceneListChannel.first()
			val tree = sceneSummary.sceneTree.root

			var numScenes = 0
			var totalWords = 0
			var longestSceneName: String? = null
			var longestSceneWords = 0
			val wordsByScene = mutableMapOf<Int, Int>()

			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = sceneEditorRepository.countWordsInScene(node.value)
					wordsByScene[node.value.id] = count
					totalWords += count
					numScenes++
					if (count > longestSceneWords) {
						longestSceneWords = count
						longestSceneName = node.value.name
					}
				}
			}

			val sceneWordCounts = wordsByScene.values.toList()
			val shortestSceneWords = sceneWordCounts.minOrNull() ?: 0
			val medianSceneWords = median(sceneWordCounts)
			val sceneWordsStdDev = stdDev(sceneWordCounts)

			yield()

			val wordsByChapter = mutableMapOf<String, Int>()
			tree.children.forEach { node ->
				val chapterName = node.value.name
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						wordsInChapter += wordsByScene[child.value.id] ?: 0
					}
				}
				wordsByChapter[chapterName] = wordsInChapter
			}

			yield()

			encyclopediaRepository.loadEntries()
			val entriesByType = mutableMapOf<String, Int>()
			encyclopediaRepository.entryListFlow.first().forEach { entry ->
				val typeKey = entry.type.name
				entriesByType[typeKey] = (entriesByType[typeKey] ?: 0) + 1
			}

			yield()

			val notesCount = notesRepository.notesListFlow.first().size
			val timelineCount = timeLineRepository.loadTimeline().events.size

			val stats = ProjectStatistics(
				numberOfScenes = numScenes,
				totalWords = totalWords,
				wordsByChapter = wordsByChapter,
				encyclopediaEntriesByType = entriesByType,
				longestSceneName = longestSceneName,
				longestSceneWords = longestSceneWords,
				shortestSceneWords = shortestSceneWords,
				medianSceneWords = medianSceneWords,
				sceneWordsStdDev = sceneWordsStdDev,
				numberOfNotes = notesCount,
				numberOfTimelineEvents = timelineCount,
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
fun SceneEditorRepository.countWordsInScene(sceneItem: SceneItem): Int {
	val markdown = loadSceneMarkdownRaw(sceneItem)
	val count = wordRegex.findAll(markdown.trim()).count()
	return count
}

private val wordRegex = Regex("""(\s+|(\r\n|\r|\n))""")
