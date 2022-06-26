package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.inject

class SceneListComponent(
    componentContext: ComponentContext,
    projectDef: ProjectDef,
    selectedSceneDef: SharedFlow<SceneDef?>,
    private val sceneSelected: (sceneDef: SceneDef) -> Unit
) : SceneList, ComponentContext by componentContext {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    private val _state = MutableValue(SceneList.State(projectDef = projectDef))
    override val state: Value<SceneList.State> = _state

    override fun onSceneSelected(sceneDef: SceneDef) {
        sceneSelected(sceneDef)
        _state.reduce {
            it.copy(selectedSceneDef = sceneDef)
        }
    }

    override fun updateSceneOrder(sceneDefs: List<SceneDef>) {
        _state.value = state.value.copy(sceneDefs = sceneDefs)
    }

    override fun moveScene(from: Int, to: Int) {
        projectEditor.moveScene(from, to)
        loadScenes()
    }

    override fun loadScenes() {
        _state.reduce {
            val scenes = projectEditor.getScenes()
            it.copy(sceneDefs = scenes)
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

    init {
        Napier.d { "Project editor: " + projectEditor.projectDef.name }

        loadScenes()

        selectedSceneDef.onEach { scene ->
            _state.reduce { it.copy(selectedSceneDef = scene) }
        }
    }
}