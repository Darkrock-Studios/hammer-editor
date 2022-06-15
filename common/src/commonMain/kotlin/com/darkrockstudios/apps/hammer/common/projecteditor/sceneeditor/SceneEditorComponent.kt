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
    private val scene: Scene,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeSceneEditor: () -> Unit
) : SceneEditor, ComponentContext by componentContext, Lifecycle.Callbacks {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(scene.project)

    private val _value = MutableValue(SceneEditor.State(scene = scene))
    override val state: Value<SceneEditor.State> = _value

    init {
        loadSceneContent()

        lifecycle.subscribe(this)
    }

    override fun loadSceneContent() {
        _value.reduce {
            val newContent = projectEditor.loadSceneContent(scene)
            it.copy(sceneContent = newContent)
        }
    }

    override fun storeSceneContent(content: String) =
        projectEditor.storeSceneContent(SceneContent(scene, content))

    override fun onContentChanged(content: String) =
        projectEditor.onContentChanged(SceneContent(scene, content))

    override fun addEditorMenu() {
        Napier.d("addEditorMenu")
        val item = MenuItemDescriptor("scene-editor-close", "Close", "") {
            Napier.d("Scene close selected")
            closeSceneEditor()
        }
        val menu = MenuDescriptor("scene-editor", "Scene", listOf(item))
        addMenu(menu)
    }

    override fun removeEditorMenu() {
        removeMenu("scene-editor")
    }

    override fun onStart() {
        addEditorMenu()
    }

    override fun onStop() {
        removeEditorMenu()
    }
}