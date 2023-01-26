package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.projectInject
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SceneListComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	selectedSceneItem: SharedFlow<SceneItem?>,
	private val sceneSelected: (sceneDef: SceneItem) -> Unit
) : ProjectComponentBase(projectDef, componentContext), SceneList {

	private val projectEditor: ProjectEditorRepository by projectInject()

	private val _state = MutableValue(SceneList.State(projectDef = projectDef))
	override val state: Value<SceneList.State> = _state

	init {
		Napier.d { "Project editor: " + projectEditor.projectDef.name }

		loadScenes()

		watchSelectedScene(selectedSceneItem)

		projectEditor.subscribeToSceneUpdates(scope, ::onSceneListUpdate)
		projectEditor.subscribeToBufferUpdates(null, scope, ::onSceneBufferUpdate)
	}

	private fun watchSelectedScene(selectedSceneItem: SharedFlow<SceneItem?>) {
		scope.launch {
			selectedSceneItem.collect { scene ->
				withContext(mainDispatcher) {
					Napier.d("Scene Selected: $scene")
					_state.reduce { it.copy(selectedSceneItem = scene) }
				}
			}
		}
	}

	override fun onSceneSelected(sceneDef: SceneItem) {
		sceneSelected(sceneDef)
		_state.reduce {
			it.copy(selectedSceneItem = sceneDef)
		}
	}

	override fun moveScene(moveRequest: MoveRequest) {
		projectEditor.moveScene(moveRequest)
	}

	override fun loadScenes() {
		_state.reduce {
			val scenes = projectEditor.getSceneSummaries()
			it.copy(sceneSummary = scenes)
		}
	}

	override fun createScene(parent: SceneItem?, sceneName: String) {
		val foundParent = if (parent?.isRootScene == true) {
			null
		} else {
			parent
		}

		val newSceneItem = projectEditor.createScene(foundParent, sceneName)
		if (newSceneItem != null) {
			Napier.i("Scene created: $sceneName")
			sceneSelected(newSceneItem)
		} else {
			Napier.w("Failed to create Scene: $sceneName")
		}
	}

	override fun createGroup(parent: SceneItem?, groupName: String) {
		val foundParent = if (parent?.isRootScene == true) {
			null
		} else {
			parent
		}

		val newSceneItem = projectEditor.createGroup(foundParent, groupName)
		if (newSceneItem != null) {
			Napier.i("Group created: $groupName")
		} else {
			Napier.w("Failed to create Group: $groupName")
		}
	}

	override fun deleteScene(scene: SceneItem) {
		when (scene.type) {
			SceneItem.Type.Scene -> {
				if (!projectEditor.deleteScene(scene)) {
					Napier.e("Failed to delete Scene: ${scene.id} - ${scene.name}")
				}
			}

			SceneItem.Type.Group -> {
				if (!projectEditor.deleteGroup(scene)) {
					Napier.e("Failed to delete Scene: ${scene.id} - ${scene.name}")
				}
			}

			SceneItem.Type.Root -> throw IllegalStateException("Cannot delete Root")
		}
	}

	override fun onSceneListUpdate(scenes: SceneSummary) {
		_state.reduce {
			it.copy(
				sceneSummary = scenes
			)
		}
	}

	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {
		val oldSummary = _state.value.sceneSummary ?: return
		_state.reduce { oldState ->
			val updated = oldSummary.hasDirtyBuffer.toMutableSet()
			if (sceneBuffer.dirty) {
				updated.add(sceneBuffer.content.scene.id)
			} else {
				updated.remove(sceneBuffer.content.scene.id)
			}

			oldState.copy(
				sceneSummary = oldSummary.copy(
					hasDirtyBuffer = updated
				)
			)
		}
	}
}