package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.projecteditor.DetailsRouter
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.inject

class SceneListComponent(
    componentContext: ComponentContext,
    projectDef: ProjectDef,
    selectedSceneDef: SharedFlow<SceneDef?>,
    private val sceneSelected: (sceneDef: SceneDef) -> Unit,
    private val detailsRouter: DetailsRouter
) : ComponentBase(componentContext), SceneList {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor: ProjectEditorRepository =
        projectRepository.getProjectEditor(projectDef)

    private val _state = MutableValue(SceneList.State(projectDef = projectDef))
    override val state: Value<SceneList.State> = _state

    init {
        projectEditor.subscribeToSceneUpdates(scope) { scenes ->
            _state.reduce {
                it.copy(
                    scenes = scenes
                )
            }
        }
    }

    override fun onSceneSelected(sceneDef: SceneDef) {
        sceneSelected(sceneDef)
        _state.reduce {
            it.copy(selectedSceneDef = sceneDef)
        }
    }

    override fun updateSceneOrder(scenes: List<SceneSummary>) {
        _state.value = state.value.copy(scenes = scenes)
    }

    override fun moveScene(from: Int, to: Int) {
        projectEditor.moveScene(from, to)
    }

    override fun loadScenes() {
        _state.reduce {
            val scenes = projectEditor.getSceneSummaries()
            it.copy(scenes = scenes)
        }
    }

    override fun createScene(sceneName: String) {
        val newSceneDef = projectEditor.createScene(sceneName)
        if (newSceneDef != null) {
            Napier.i("Scene created: $sceneName")
            detailsRouter.showScene(newSceneDef)
        } else {
            Napier.w("Failed to create Scene: $sceneName")
        }
    }

    override fun deleteScene(sceneDef: SceneDef) {
        if (!projectEditor.deleteScene(sceneDef)) {
            Napier.e("Failed to delete Scene: ${sceneDef.id} - ${sceneDef.name}")
        }
    }

    private fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {
        val currentSummary =
            _state.value.scenes.find { it.sceneDef.id == sceneBuffer.content.sceneDef.id }

        if (currentSummary != null && currentSummary.hasDirtyBuffer != sceneBuffer.dirty) {
            _state.reduce {
                val index = it.scenes.indexOfFirst { summary ->
                    summary.sceneDef.id == sceneBuffer.content.sceneDef.id
                }
                val oldSummary = it.scenes[index]
                val newSummary = oldSummary.copy(hasDirtyBuffer = sceneBuffer.dirty)
                val newList = it.scenes.toMutableList()
                newList[index] = newSummary

                it.copy(scenes = newList)
            }
        }
    }

    init {
        Napier.d { "Project editor: " + projectEditor.projectDef.name }

        loadScenes()

        selectedSceneDef.onEach { scene ->
            _state.reduce { it.copy(selectedSceneDef = scene) }
        }

        projectEditor.subscribeToBufferUpdates(null, scope, ::onSceneBufferUpdate)
    }
}