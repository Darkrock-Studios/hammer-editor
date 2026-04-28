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
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
	private val referenceIndexService: ReferenceIndexService by projectInject()

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
		startMetadataStore()

		scope.launch {
			sceneEditor.getSceneBuffer(originalSceneItem)?.let { sceneBuf ->
				onBufferUpdate(sceneBuf)
			}

			loadSceneData()
			loadMetadataData()

			sceneEditor.subscribeToSceneUpdates(scope, ::onSceneTreeUpdate)
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

		val sceneText = sceneEditor.getSceneBuffer(originalSceneItem)?.content?.coerceMarkdown()
			?: sceneEditor.loadSceneMarkdownRaw(originalSceneItem)
		val defById = HashMap<Int, EntryDef>()
		val suggestions = referenceIndexService
			.computeSuggestionsForScene(originalSceneItem.id, sceneText, metadata)
			.mapNotNull { suggestion ->
				val def = defById.getOrPut(suggestion.entryId) {
					encyclopediaRepository.findEntryDef(suggestion.entryId) ?: return@mapNotNull null
				}
				SceneMetadataPanel.SuggestedRef(entryDef = def, matchedAlias = suggestion.matchedAlias)
			}
			.sortedBy { it.entryDef.name.lowercase() }

		withContext(dispatcherMain) {
			_state.getAndUpdate {
				if (it.confirmedRefs == confirmed &&
					it.dismissedRefs == dismissed &&
					it.suggestedRefs == suggestions
				) {
					it
				} else {
					it.copy(
						confirmedRefs = confirmed,
						dismissedRefs = dismissed,
						suggestedRefs = suggestions,
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
				sceneEditor.storeMetadata(metadata, originalSceneItem.id)
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

	override fun confirmReference(entryId: Int) {
		mutateMetadata { it.copy(
			confirmedReferences = it.confirmedReferences + entryId,
			dismissedReferences = it.dismissedReferences - entryId,
		) }
	}

	override fun unconfirmReference(entryId: Int) {
		mutateMetadata { it.copy(confirmedReferences = it.confirmedReferences - entryId) }
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
			sceneEditor.storeMetadata(state.value.metadata, originalSceneItem.id)
		}
	}

	companion object {
		val STORE_COOL_DOWN = 500.milliseconds
	}
}