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
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

	private val matchableCacheLock = reentrantLock()
	private var matchableCache: List<MatchableEntry>? = null

	init {
		projectScope.scope.registerCallback(this)
		serviceScope.launch {
			encyclopediaRepository.entryListFlow.collect {
				matchableCacheLock.withLock { matchableCache = null }
			}
		}
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

	/**
	 * Snapshot of scene IDs the inverted index currently reports as confirming
	 * this entry. Ensures the index is loaded (recalculating if dirty/missing)
	 * before reading. Used by [CleanupReferencesOnEntryDeleteUseCase] to walk
	 * exactly the scenes that need rewriting on entry delete.
	 *
	 * Returns the cached *confirmed-only* set. Scenes that have the entry in
	 * `dismissedReferences` only (never confirmed) are not in the cache and
	 * must heal lazily via the write-time scrub.
	 */
	suspend fun getScenesReferencing(entryId: Int): Set<Int> {
		val index = loadIndex()
		return index.entryToScenes[entryId].orEmpty()
	}

	/**
	 * Returns the entry references that the matcher would auto-add for this scene
	 * if the user saved it now: text matches that aren't already confirmed and
	 * aren't dismissed. Result is deduped per-entry, attributing each to the first
	 * matched name/alias.
	 */
	suspend fun computeAutoReferencesForScene(
		sceneId: Int,
		sceneText: String,
		metadata: SceneMetadata?,
	): List<EntrySuggestion> {
		val confirmed = metadata?.confirmedReferences.orEmpty()
		val dismissed = metadata?.dismissedReferences.orEmpty()

		val matchable = matchableEntries()
		if (matchable.isEmpty()) return emptyList()

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

	/**
	 * Walks all live + archived scenes and returns the IDs of those whose text
	 * matches the given entry's name or any of its aliases AND where the entry
	 * is not yet in the scene's confirmed or dismissed sets.
	 */
	suspend fun findScenesMatchingEntry(entryId: Int, names: List<String>): List<Int> {
		if (names.none { it.isNotBlank() }) return emptyList()
		val matchable = listOf(MatchableEntry(entryId, names))
		val results = mutableListOf<Int>()

		suspend fun consider(sceneItem: SceneItem) {
			val metadata = sceneEditorRepository.loadSceneMetadata(sceneItem.id)
			if (entryId in metadata.confirmedReferences) return
			if (entryId in metadata.dismissedReferences) return
			val text = if (sceneItem.archived) {
				val scenePath = sceneEditorRepository
					.resolveScenePathFromFilesystemIncludingArchived(sceneItem.id) ?: return
				sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, scenePath)
			} else {
				sceneEditorRepository.loadSceneMarkdownRaw(sceneItem)
			}
			val hits = matcher.findMatches(text, matchable)
			if (hits.isNotEmpty()) results.add(sceneItem.id)
		}

		val sceneSummary = sceneEditorRepository.sceneListChannel.first()
		sceneSummary.sceneTree.root.forEach { node ->
			if (node.value.type == SceneItem.Type.Scene) {
				consider(node.value)
				yield()
			}
		}
		sceneEditorRepository.getArchivedScenes().forEach { archived ->
			consider(archived)
			yield()
		}
		return results
	}

	private suspend fun matchableEntries(): List<MatchableEntry> {
		matchableCacheLock.withLock { matchableCache }?.let { return it }

		val entries = encyclopediaRepository.ensureEntriesLoaded()
			.filter { it.type in config.enabledEntryTypes }
		if (entries.isEmpty()) {
			matchableCacheLock.withLock { matchableCache = emptyList() }
			return emptyList()
		}

		val rebuilt = coroutineScope {
			entries.map { entryDef ->
				async {
					val container = encyclopediaRepository.loadEntry(entryDef)
					MatchableEntry(
						entryId = entryDef.id,
						names = listOf(entryDef.name) + container.entry.aliases,
					)
				}
			}.awaitAll()
		}
		matchableCacheLock.withLock { matchableCache = rebuilt }
		return rebuilt
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
