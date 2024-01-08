package com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.SceneEditorRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SceneMetadataComponent(
	componentContext: ComponentContext,
	private val originalSceneItem: SceneItem,
) : ProjectComponentBase(originalSceneItem.projectDef, componentContext),
	SceneMetadata {

	private val sceneEditor: SceneEditorRepository by projectInject()

	private val _state = MutableValue(
		SceneMetadata.State(originalSceneItem)
	)
	override val state: Value<SceneMetadata.State> = _state

	private var bufferUpdateSubscription: Job? = null

	override fun onCreate() {
		super.onCreate()
		subscribeToBufferUpdates()

		scope.launch {
			sceneEditor.getSceneBuffer(originalSceneItem)?.let { sceneBuf ->
				onBufferUpdate(sceneBuf)
			}
		}
	}

	private fun subscribeToBufferUpdates() {
		Napier.d { "SceneMetadataComponent start collecting buffer updates" }

		bufferUpdateSubscription?.cancel()

		bufferUpdateSubscription =
			sceneEditor.subscribeToBufferUpdates(originalSceneItem, scope, ::onBufferUpdate)
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

	override fun onDestroy() {
		super.onDestroy()
		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription = null
	}
}