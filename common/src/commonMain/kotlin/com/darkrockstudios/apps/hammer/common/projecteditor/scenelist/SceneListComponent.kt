package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.Scene
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.inject

class SceneListComponent(
    componentContext: ComponentContext,
    project: Project,
    selectedScene: SharedFlow<Scene?>,
    private val sceneSelected: (scene: Scene) -> Unit
) : SceneList, ComponentContext by componentContext {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(project)

    private val _state = MutableValue(SceneList.State(project = project))
    override val state: Value<SceneList.State> = _state

    override fun onSceneSelected(scene: Scene) {
        sceneSelected(scene)
        _state.reduce {
            it.copy(selectedScene = scene)
        }
    }

    override fun loadScenes() {
        _state.reduce {
            val scenes = projectEditor.getScenes()
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

    override fun deleteScene(scene: Scene) {
        if (projectEditor.deleteScene(scene)) {
            loadScenes()
        }
    }

    init {
        Napier.d { "Project editor: " + projectEditor.project.name }

        loadScenes()

        selectedScene.onEach { scene ->
            _state.reduce { it.copy(selectedScene = scene) }
        }
    }
}