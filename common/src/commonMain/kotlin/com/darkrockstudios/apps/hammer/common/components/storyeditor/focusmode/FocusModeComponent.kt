package com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.decreaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.increaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.koin.core.component.inject

class FocusModeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val sceneItem: SceneItem,
	private val closeFocusMode: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), FocusMode {

	private val settingsRepository: GlobalSettingsRepository by inject()
	private val sceneEditor: SceneEditorRepository by projectInject()

	private var bufferUpdateSubscription: Job? = null
	override var lastForceUpdate = MutableValue<Long>(0)

	private val _state = MutableValue(FocusMode.State(projectDef, sceneItem))
	override val state = _state

	override fun onCreate() {
		super.onCreate()

		loadSceneContent()
		subscribeToBufferUpdates()
		watchSettings()
	}

	private fun loadSceneContent() {
		_state.getAndUpdate {
			val buffer = sceneEditor.loadSceneBuffer(sceneItem)
			it.copy(sceneBuffer = buffer)
		}
	}

	override fun onContentChanged(content: PlatformRichText) {
		sceneEditor.onContentChanged(
			SceneContent(
				scene = sceneItem,
				platformRepresentation = content
			),
			UpdateSource.Editor
		)
	}

	override fun decreaseTextSize() {
		scope.launch {
			val size = decreaseEditorTextSize(state.value.textSize)
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = size
				)
			}
		}
	}

	override fun increaseTextSize() {
		scope.launch {
			val size = increaseEditorTextSize(state.value.textSize)
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = size
				)
			}
		}
	}

	override fun resetTextSize() {
		scope.launch {
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = GlobalSettings.DEFAULT_FONT_SIZE
				)
			}
		}
	}

	private fun subscribeToBufferUpdates() {
		Napier.d { "FocusModeComponent start collecting buffer updates" }

		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription =
			sceneEditor.subscribeToBufferUpdates(sceneItem, scope, ::onBufferUpdate)
	}

	private fun watchSettings() {
		scope.launch {
			settingsRepository.globalSettingsUpdates.collect { settings ->
				if (settings.editorFontSize != _state.value.textSize) {
					withContext(dispatcherMain) {
						_state.getAndUpdate {
							it.copy(
								textSize = settings.editorFontSize
							)
						}
					}
				}
			}
		}
	}

	private suspend fun onBufferUpdate(sceneBuffer: SceneBuffer) = withContext(dispatcherMain) {
		_state.getAndUpdate {
			it.copy(sceneBuffer = sceneBuffer)
		}

		if (sceneBuffer.source != UpdateSource.Editor) {
			forceUpdate()
		}
	}

	private fun forceUpdate() {
		lastForceUpdate.value = Clock.System.now().epochSeconds
	}

	override fun dismiss() {
		closeFocusMode()
	}
}