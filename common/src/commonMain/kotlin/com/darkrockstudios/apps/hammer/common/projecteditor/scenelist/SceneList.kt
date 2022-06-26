package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.di.HammerComponent

interface SceneList : HammerComponent {
    val state: Value<State>
    fun onSceneSelected(sceneDef: SceneDef)
    fun updateSceneOrder(sceneDefs: List<SceneDef>)
    fun moveScene(from: Int, to: Int)
    fun loadScenes()
    fun createScene(sceneName: String)
    fun deleteScene(sceneDef: SceneDef)

    data class State(
        val projectDef: ProjectDef,
        val selectedSceneDef: SceneDef? = null,
        val sceneDefs: List<SceneDef> = emptyList()
    )
}