package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

class ReferenceIndexService(
	projectDef: ProjectDef,
	private val repository: ReferenceIndexRepository,
	private val sceneEditorRepository: SceneEditorRepository,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val matcher: NameMatcher,
	private val config: ReferenceIndexConfig,
	private val clock: Clock,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val serviceScope = CoroutineScope(dispatcherDefault)

	val indexFlow: SharedFlow<ReferenceIndex> = repository.indexFlow
	val isDirty: StateFlow<Boolean> = repository.isDirty

	private val _isCalculating = MutableStateFlow(false)
	val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

	init {
		projectScope.scope.registerCallback(this)
	}

	suspend fun loadIndex(): ReferenceIndex {
		val cached = repository.loadIndex()
		val isCurrentSchema = cached?.schemaVersion == ReferenceIndex.CURRENT_SCHEMA_VERSION
		return if (cached != null && isCurrentSchema && !cached.isDirty) {
			cached
		} else {
			when {
				cached == null -> Napier.d("Reference index cache missing, recalculating")
				!isCurrentSchema -> Napier.i(
					"Reference index schema is outdated (was ${cached.schemaVersion}, " +
						"expected ${ReferenceIndex.CURRENT_SCHEMA_VERSION}); recalculating"
				)

				else -> Napier.d("Reference index dirty, recalculating")
			}
			recalculate()
		}
	}

	suspend fun recalculate(): ReferenceIndex {
		_isCalculating.value = true
		val startTime = clock.now()
		try {
			val map = mutableMapOf<Int, MutableSet<Int>>()

			val sceneSummary = sceneEditorRepository.sceneListChannel.first()
			sceneSummary.sceneTree.root.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val metadata = sceneEditorRepository.loadSceneMetadata(node.value.id)
					accumulate(map, node.value.id, metadata.confirmedReferences)
				}
			}

			yield()

			sceneEditorRepository.getArchivedScenes().forEach { archived ->
				val metadata = sceneEditorRepository.loadSceneMetadata(archived.id)
				accumulate(map, archived.id, metadata.confirmedReferences)
			}

			val index = ReferenceIndex(
				schemaVersion = ReferenceIndex.CURRENT_SCHEMA_VERSION,
				isDirty = false,
				lastCalculated = clock.now(),
				entryToScenes = map.mapValues { it.value.toSet() },
			)

			repository.saveIndex(index)
			repository.clearDirty()

			val duration = clock.now() - startTime
			Napier.i("Reference index calculated in ${duration.inWholeMilliseconds}ms: ${index.entryToScenes.size} entries")
			return index
		} finally {
			_isCalculating.value = false
		}
	}

	fun flowForEntry(entryId: Int): Flow<Set<Int>> =
		indexFlow.map { it.entryToScenes[entryId].orEmpty() }

	suspend fun computeSuggestionsForScene(
		sceneId: Int,
		sceneText: String,
		metadata: SceneMetadata?,
	): List<EntrySuggestion> {
		val confirmed = metadata?.confirmedReferences.orEmpty()
		val dismissed = metadata?.dismissedReferences.orEmpty()

		val entries = encyclopediaRepository.entryListFlow.first()
			.filter { it.type in config.enabledEntryTypes }

		if (entries.isEmpty()) return emptyList()

		val matchable = entries.map { entryDef ->
			val container = encyclopediaRepository.loadEntry(entryDef)
			MatchableEntry(
				entryId = entryDef.id,
				names = listOf(entryDef.name) + container.entry.aliases,
			)
		}

		val hits = matcher.findMatches(sceneText, matchable)
		if (hits.isEmpty()) return emptyList()

		val firstHitByEntry = linkedMapOf<Int, EntrySuggestion>()
		for (hit in hits) {
			if (hit.entryId in confirmed) continue
			if (hit.entryId in dismissed) continue
			if (hit.entryId !in firstHitByEntry) {
				firstHitByEntry[hit.entryId] = EntrySuggestion(hit.entryId, hit.matchedText)
			}
		}
		return firstHitByEntry.values.toList()
	}

	private fun accumulate(map: MutableMap<Int, MutableSet<Int>>, sceneId: Int, entryIds: Set<Int>) {
		for (entryId in entryIds) {
			map.getOrPut(entryId) { mutableSetOf() }.add(sceneId)
		}
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("ReferenceIndexService Closed")
	}
}
