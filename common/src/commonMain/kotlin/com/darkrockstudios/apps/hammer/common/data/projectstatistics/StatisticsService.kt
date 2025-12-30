package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
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
	 * If cache is missing or dirty, calculates fresh statistics.
	 */
	suspend fun loadStatistics(): ProjectStatistics {
		val cached = statisticsRepository.loadStatistics()
		return if (cached != null && !cached.isDirty) {
			Napier.d("Loaded statistics from cache")
			cached
		} else {
			if (cached?.isDirty == true) {
				Napier.d("Cache is dirty, will recalculate")
			}
			Napier.d("Cache missing or dirty, calculating statistics")
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

			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = sceneEditorRepository.countWordsInScene(node.value)
					totalWords += count
					numScenes++
				}
			}

			yield()

			val wordsByChapter = mutableMapOf<String, Int>()
			tree.children.forEach { node ->
				val chapterName = node.value.name
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						val count = sceneEditorRepository.countWordsInScene(child.value)
						wordsInChapter += count
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

			val stats = ProjectStatistics(
				numberOfScenes = numScenes,
				totalWords = totalWords,
				wordsByChapter = wordsByChapter,
				encyclopediaEntriesByType = entriesByType,
				isDirty = false,
				lastCalculated = clock.now()
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
