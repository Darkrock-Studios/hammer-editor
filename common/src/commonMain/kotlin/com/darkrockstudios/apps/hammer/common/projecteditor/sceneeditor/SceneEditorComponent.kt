package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.*
import io.github.aakira.napier.Napier
import org.koin.core.component.inject


class SceneEditorComponent(
    componentContext: ComponentContext,
    private val sceneDef: SceneDef,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeSceneEditor: () -> Unit
) : SceneEditor, ComponentContext by componentContext, Lifecycle.Callbacks {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(sceneDef.projectDef)

    private val _state = MutableValue(SceneEditor.State(sceneDef = sceneDef))
    override val state: Value<SceneEditor.State> = _state

    init {
        loadSceneContent()

        lifecycle.subscribe(this)
    }

    override fun loadSceneContent() {
        _state.reduce {
            val newContent = projectEditor.loadSceneContent(sceneDef)
            it.copy(sceneContent = newContent)
        }
    }

    override fun storeSceneContent(content: String) =
        projectEditor.storeSceneContent(SceneContent(sceneDef, content))

    override fun onContentChanged(content: String) =
        projectEditor.onContentChanged(SceneContent(sceneDef, content))

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