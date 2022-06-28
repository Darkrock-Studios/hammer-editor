package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.core.component.inject


class SceneEditorComponent(
    componentContext: ComponentContext,
    private val sceneDef: SceneDef,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeSceneEditor: () -> Unit
) : ComponentBase(componentContext), SceneEditor {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(sceneDef.projectDef)

    private val _state = MutableValue(SceneEditor.State(sceneDef = sceneDef))
    override val state: Value<SceneEditor.State> = _state

    init {
        loadSceneContent()

        scope.launch {
            Napier.d { "SceneEditorComponent start collecting buffer updates" }
            projectEditor.subscribeToBufferUpdates(sceneDef, ::onBufferUpdate)
        }
    }

    private fun onBufferUpdate(sceneBuffer: SceneBuffer) {
        Napier.d { "SceneEditorComponent scene buffer updated" }
        _state.reduce {
            it.copy(sceneBuffer = sceneBuffer)
        }
    }

    override fun loadSceneContent() {
        _state.reduce {
            val buffer = projectEditor.loadSceneBuffer(sceneDef)
            it.copy(sceneBuffer = buffer)
        }
    }

    override fun storeSceneContent() =
        projectEditor.storeSceneBuffer(sceneDef)

    override fun onContentChanged(content: String) {
        projectEditor.onContentChanged(SceneContent(sceneDef, content))
    }

    override fun addEditorMenu() {
        val item = MenuItemDescriptor("scene-editor-close", "Close", "") {
            Napier.d("Scene close selected")
            closeSceneEditor()
        }
        val menu = MenuDescriptor(getMenuId(), "Scene", listOf(item))
        addMenu(menu)
    }

    override fun removeEditorMenu() {
        removeMenu(getMenuId())
    }

    private fun getMenuId(): String {
        return "scene-editor-${sceneDef.order}-${sceneDef.name}"
    }

    override fun onStart() {
        addEditorMenu()
    }

    override fun onStop() {
        removeEditorMenu()
    }
}