package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.di.HammerComponent

interface SceneEditor : HammerComponent {
    val state: Value<State>

    fun addEditorMenu()
    fun removeEditorMenu()
    fun loadSceneContent()
    fun storeSceneContent(content: String): Boolean
    fun onContentChanged(content: String)

    data class State(
        val sceneDef: SceneDef,
        val sceneContent: String? = null
    )
}