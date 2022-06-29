package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.di.HammerComponent

interface SceneEditor : HammerComponent {
    val state: Value<State>
    var lastDiscarded: MutableValue<Long>

    fun addEditorMenu()
    fun removeEditorMenu()
    fun loadSceneContent()
    fun storeSceneContent(): Boolean
    fun onContentChanged(content: PlatformRichText)

    data class State(
        val sceneDef: SceneDef,
        val sceneBuffer: SceneBuffer? = null
    )
}