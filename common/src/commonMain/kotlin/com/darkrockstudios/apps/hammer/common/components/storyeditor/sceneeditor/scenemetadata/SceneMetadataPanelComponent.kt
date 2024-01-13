package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository.SceneMetadata
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class SceneMetadataPanelComponent(
	componentContext: ComponentContext,
	private val originalSceneItem: SceneItem,
) : ProjectComponentBase(originalSceneItem.projectDef, componentContext),
	SceneMetadataPanel {

	private val sceneEditor: SceneEditorRepository by projectInject()

	private val _state = MutableValue(
		SceneMetadataPanel.State(originalSceneItem)
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

			val metadata = sceneEditor.loadSceneMetadata(originalSceneItem.id)
			_state.getAndUpdate {
				it.copy(
					metadata = metadata
				)
			}
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

	override fun onDestroy() {
		super.onDestroy()
		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription = null
	}

	companion object {
		val STORE_COOL_DOWN = 500.milliseconds
	}
}