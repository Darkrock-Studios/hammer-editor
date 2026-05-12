package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.time.Duration.Companion.milliseconds

class SceneMetadataPanelComponent(
	componentContext: ComponentContext,
	private val originalSceneItem: SceneItem,
	private val showEntry: (EntryDef) -> Unit,
) : ProjectComponentBase(originalSceneItem.projectDef, componentContext),
	SceneMetadataPanel {

	private val appScope: CoroutineScope by inject(named(APP_SCOPE))
	private val sceneEditor: SceneEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val scrubInvalidReferences: ScrubInvalidReferencesUseCase by projectInject()

	private val searchableEntries = MutableStateFlow<List<SearchableEntry>>(emptyList())

	private val _state = MutableValue(
		SceneMetadataPanel.State(
			originalSceneItem,
			"",
			""
		)
	)
	override val state: Value<SceneMetadataPanel.State> = _state

	private var bufferUpdateSubscription: Job? = null

	private val _metadataUpdateFlow = MutableSharedFlow<SceneMetadata>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	private val metadataStoreFlow: SharedFlow<SceneMetadata> = _metadataUpdateFlow
	private var metadataStoreJob: Job? = null

	override fun onCreate() {
		super.onCreate()
		subscribeToBufferUpdates()
		subscribeToMetadataUpdates()
		startMetadataStore()

		scope.launch {
			sceneEditor.getSceneBuffer(originalSceneItem)?.let { sceneBuf ->
				onBufferUpdate(sceneBuf)
			}

			loadSceneData()
			loadMetadataData()

			sceneEditor.subscribeToSceneUpdates(scope, ::onSceneTreeUpdate)
		}

		scope.launch {
			encyclopediaRepository.entryListFlow.collect { defs ->
				rebuildSearchableEntries(defs)
				refreshReferences()
			}
		}

		// Force the encyclopedia to publish its entry list so the search cache and
		// chip resolver have data even if no other part of the UI has triggered a
		// load yet.
		scope.launch { encyclopediaRepository.ensureEntriesLoaded() }
	}

	private fun subscribeToMetadataUpdates() {
		scope.launch {
			sceneEditor.metadataUpdateFlow
				.filter { (sceneId, _) -> sceneId == originalSceneItem.id }
				.collect { (_, external) ->
					val current = state.value.metadata
					val refsChanged = current.confirmedReferences != external.confirmedReferences ||
						current.dismissedReferences != external.dismissedReferences
					if (!refsChanged) return@collect

					// Merge: take ref fields from the external write (e.g. auto-confirm on save),
					// keep user-editable fields (outline / notes / currentDraftName) from local state.
					val merged = current.copy(
						confirmedReferences = external.confirmedReferences,
						dismissedReferences = external.dismissedReferences,
					)
					withContext(dispatcherMain) {
						_state.getAndUpdate { it.copy(metadata = merged) }
					}
					// Re-emit so a pending debounced write picks up the merged state instead of
					// the stale value it had queued.
					if (_metadataUpdateFlow.tryEmit(merged).not()) {
						Napier.w { "Failed to re-emit merged metadata after external update" }
					}
					refreshReferences()
				}
		}
	}

	private suspend fun rebuildSearchableEntries(defs: List<EntryDef>) {
		searchableEntries.value = defs.map { def ->
			val container = encyclopediaRepository.loadEntry(def)
			val terms = (listOf(def.name) + container.entry.aliases)
				.filter { it.isNotBlank() }
				.map { it.lowercase() }
			SearchableEntry(entryDef = def, lowerCaseSearchTerms = terms)
		}
	}

	private fun onSceneTreeUpdate(sceneSummary: SceneSummary) {
		scope.launch {
			loadSceneData()
		}
	}

	private suspend fun loadMetadataData() {
		val metadata = sceneEditor.loadSceneMetadata(originalSceneItem.id)
		_state.getAndUpdate {
			it.copy(
				metadata = metadata,
			)
		}
		refreshReferences()
	}

	private suspend fun refreshReferences() {
		val metadata = state.value.metadata
		val confirmed = metadata.confirmedReferences.mapNotNull { encyclopediaRepository.findEntryDef(it) }
			.sortedBy { it.name.lowercase() }
		val dismissed = metadata.dismissedReferences.mapNotNull { encyclopediaRepository.findEntryDef(it) }
			.sortedBy { it.name.lowercase() }

		withContext(dispatcherMain) {
			_state.getAndUpdate {
				if (it.confirmedRefs == confirmed && it.dismissedRefs == dismissed) {
					it
				} else {
					it.copy(
						confirmedRefs = confirmed,
						dismissedRefs = dismissed,
					)
				}
			}
		}
	}

	private suspend fun loadSceneData() {
		val path = sceneEditor.getSceneFilePath(originalSceneItem.id)
		val filename = sceneEditor.getSceneFilename(path)
		_state.getAndUpdate {
			it.copy(
				filename = filename,
				path = path.path
			)
		}
	}

	private fun subscribeToBufferUpdates() {
		Napier.d { "SceneMetadataComponent start collecting buffer updates" }

		bufferUpdateSubscription?.cancel()

		bufferUpdateSubscription =
			sceneEditor.subscribeToBufferUpdates(originalSceneItem, scope, ::onBufferUpdate)
	}

	private fun startMetadataStore() {
		metadataStoreJob = scope.launch {
			metadataStoreFlow.debounceUntilQuiescent(STORE_COOL_DOWN).collect { metadata ->
				// Self-healing fail-safe: drop reference IDs whose entry no longer exists
				sceneEditor.storeMetadata(scrubInvalidReferences(metadata), originalSceneItem.id)
			}
		}
	}

	private suspend fun onBufferUpdate(sceneBuffer: SceneBuffer) = withContext(dispatcherDefault) {
		val wordCount = calculateWordCount(sceneBuffer)

		withContext(dispatcherMain) {
			_state.getAndUpdate {
				it.copy(wordCount = wordCount)
			}
		}
		refreshReferences()
	}

	private val wordsRegex = "\\s+".toRegex()
	private fun calculateWordCount(sceneBuffer: SceneBuffer): Int {
		// TODO hopefully in the future we'll be able to access some raw text
		// without having to convert to markdown
		val text = sceneBuffer.content.coerceMarkdown()

		return if (text.isEmpty()) {
			0
		} else {
			val words = text.split(wordsRegex)
			words.size
		}
	}

	override fun updateOutline(text: String) {
		_state.getAndUpdate {
			val updated = it.metadata.copy(outline = text)
			if (_metadataUpdateFlow.tryEmit(updated).not()) {
				Napier.w { "Failed to emit metadataUpdate for Outline" }
			}
			it.copy(
				metadata = updated
			)
		}
	}

	override fun updateNotes(text: String) {
		_state.getAndUpdate {
			val updated = it.metadata.copy(notes = text)
			if (_metadataUpdateFlow.tryEmit(updated).not()) {
				Napier.w { "Failed to emit metadataUpdate for Notes" }
			}
			it.copy(
				metadata = it.metadata.copy(notes = text)
			)
		}
	}

	override fun updateDraftName(text: String) {
		_state.getAndUpdate {
			val updated = it.metadata.copy(currentDraftName = text)
			if (_metadataUpdateFlow.tryEmit(updated).not()) {
				Napier.w { "Failed to emit metadataUpdate for Draft Name" }
			}
			it.copy(
				metadata = it.metadata.copy(currentDraftName = text)
			)
		}
	}

	override fun validateDraftName(text: String): Boolean {
		return SceneDraftsDatasource.validDraftName(text)
	}

	override fun addConfirmedReference(entryId: Int) {
		// Add to confirmed and remove from dismissed in one write so picking a
		// previously-dismissed entry from search un-dismisses it cleanly.
		mutateMetadata {
			it.copy(
				confirmedReferences = it.confirmedReferences + entryId,
				dismissedReferences = it.dismissedReferences - entryId,
			)
		}
	}

	override fun searchEntriesForAdd(query: String, maxResults: Int): List<SceneMetadataPanel.AddSuggestion> {
		val metadata = state.value.metadata
		return filterEntriesForAdd(
			query = query,
			entries = searchableEntries.value,
			confirmedIds = metadata.confirmedReferences,
			dismissedIds = metadata.dismissedReferences,
			maxResults = maxResults,
		)
	}

	override fun dismissReference(entryId: Int) {
		mutateMetadata { it.copy(
			confirmedReferences = it.confirmedReferences - entryId,
			dismissedReferences = it.dismissedReferences + entryId,
		) }
	}

	override fun restoreDismissedReference(entryId: Int) {
		mutateMetadata { it.copy(dismissedReferences = it.dismissedReferences - entryId) }
	}

	override fun navigateToEntry(entryDef: EntryDef) {
		showEntry(entryDef)
	}

	private fun mutateMetadata(transform: (SceneMetadata) -> SceneMetadata) {
		_state.getAndUpdate {
			val updated = transform(it.metadata)
			if (_metadataUpdateFlow.tryEmit(updated).not()) {
				Napier.w { "Failed to emit metadataUpdate for reference change" }
			}
			it.copy(metadata = updated)
		}
		scope.launch { refreshReferences() }
	}

	override fun onDestroy() {
		super.onDestroy()
		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription = null

		appScope.launch {
			sceneEditor.storeMetadata(
				scrubInvalidReferences(state.value.metadata),
				originalSceneItem.id,
			)
		}
	}

	companion object {
		val STORE_COOL_DOWN = 500.milliseconds
	}
}