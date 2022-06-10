package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.Scene
import io.github.aakira.napier.Napier


class SceneEditorComponent(
    componentContext: ComponentContext,
    scene: Scene,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeSceneEditor: () -> Unit
): SceneEditor, ComponentContext by componentContext {

    private val _value = MutableValue(SceneEditor.State(scene = scene))
    override val state: Value<SceneEditor.State> = _value

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
}