package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.inject

class SceneListComponent(
    componentContext: ComponentContext,
    projectDef: ProjectDef,
    selectedSceneItem: SharedFlow<SceneItem?>,
    private val sceneSelected: (sceneDef: SceneItem) -> Unit
) : ComponentBase(componentContext), SceneList {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor: ProjectEditorRepository =
        projectRepository.getProjectEditor(projectDef)

    private val _state = MutableValue(SceneList.State(projectDef = projectDef))
    override val state: Value<SceneList.State> = _state

    init {
        Napier.d { "Project editor: " + projectEditor.projectDef.name }

        selectedSceneItem.onEach { scene ->
            _state.reduce { it.copy(selectedSceneItem = scene) }
        }

        projectEditor.subscribeToSceneUpdates(scope, ::onSceneListUpdate)
        projectEditor.subscribeToBufferUpdates(null, scope, ::onSceneBufferUpdate)
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
            it.copy(scenes = scenes)
        }
    }

    override fun createScene(sceneName: String) {
        val newSceneItem = projectEditor.createScene(null, sceneName)
        if (newSceneItem != null) {
            Napier.i("Scene created: $sceneName")
            sceneSelected(newSceneItem)
        } else {
            Napier.w("Failed to create Scene: $sceneName")
        }
    }

    override fun deleteScene(sceneDef: SceneItem) {
        if (!projectEditor.deleteScene(sceneDef)) {
            Napier.e("Failed to delete Scene: ${sceneDef.id} - ${sceneDef.name}")
        }
    }

    override fun onSceneListUpdate(scenes: SceneSummary) {
        _state.reduce {
            it.copy(
                scenes = scenes
            )
        }
    }

    override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {
        /*
        val currentSummary =
            _state.value.scenes.sceneTree.find { it.id == sceneBuffer.content.scene.id }
        val hasDirty = _state.value.scenes.hasDirtyBuffer.contains(sceneBuffer.content.scene.id)

        if (currentSummary != null && hasDirty != sceneBuffer.dirty) {
            _state.reduce {
                val index = it.scenes.indexOfFirst { summary ->
                    summary.sceneDef.id == sceneBuffer.content.scene.id
                }
                val oldSummary = it.scenes[index]
                val newSummary = oldSummary.copy(hasDirtyBuffer = sceneBuffer.dirty)
                val newList = it.scenes.toMutableList()
                newList[index] = newSummary

                it.copy(scenes = newList)
            }
        }
        */
    }
}