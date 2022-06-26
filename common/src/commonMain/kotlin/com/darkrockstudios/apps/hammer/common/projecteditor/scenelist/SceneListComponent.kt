package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class SceneListComponent(
    componentContext: ComponentContext,
    projectDef: ProjectDef,
    selectedSceneDef: SharedFlow<SceneDef?>,
    private val sceneSelected: (sceneDef: SceneDef) -> Unit
) : SceneList, ComponentContext by componentContext, Lifecycle.Callbacks {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    private val _state = MutableValue(SceneList.State(projectDef = projectDef))
    override val state: Value<SceneList.State> = _state

    private val sceneListScope = CoroutineScope(defaultDispatcher)

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
        loadScenes()
    }

    override fun loadScenes() {
        _state.reduce {
            val scenes = projectEditor.getSceneSummaries()
            it.copy(scenes = scenes)
        }
    }

    override fun createScene(sceneName: String) {
        if (projectEditor.createScene(sceneName) != null) {
            Napier.i("Scene created: $sceneName")
            loadScenes()
        } else {
            Napier.w("Failed to create Scene: $sceneName")
        }
    }

    override fun deleteScene(sceneDef: SceneDef) {
        if (projectEditor.deleteScene(sceneDef)) {
            loadScenes()
        }
    }

    override fun onDestroy() {
        sceneListScope.cancel("SceneList destroyed")
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

        lifecycle.subscribe(this)

        loadScenes()

        selectedSceneDef.onEach { scene ->
            _state.reduce { it.copy(selectedSceneDef = scene) }
        }

        sceneListScope.launch {
            projectEditor.subscribeToBufferUpdates(null, ::onSceneBufferUpdate)
        }
    }
}