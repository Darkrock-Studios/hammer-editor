package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.Scene

interface SceneEditor {
    val state: Value<State>

    fun addEditorMenu()
    fun removeEditorMenu()

    data class State(
        val scene: Scene
    )
}