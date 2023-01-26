package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.*

interface SceneList {
    val state: Value<State>
    fun onSceneSelected(sceneDef: SceneItem)
    fun moveScene(moveRequest: MoveRequest)
    fun loadScenes()
    fun createScene(parent: SceneItem?, sceneName: String)
    fun createGroup(parent: SceneItem?, groupName: String)
    fun deleteScene(scene: SceneItem)

    fun onSceneListUpdate(scenes: SceneSummary)
    fun onSceneBufferUpdate(sceneBuffer: SceneBuffer)

    data class State(
        val projectDef: ProjectDef,
        val selectedSceneItem: SceneItem? = null,
        val sceneSummary: SceneSummary? = null
    )
}