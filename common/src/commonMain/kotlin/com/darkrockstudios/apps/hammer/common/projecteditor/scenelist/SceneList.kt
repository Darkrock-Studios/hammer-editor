package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.Scene
import com.darkrockstudios.apps.hammer.common.di.HammerComponent

interface SceneList : HammerComponent {
    val state: Value<State>
    fun onSceneSelected(scene: Scene)

    data class State(
        val project: Project,
        val selectedScene: Scene? = null,
        val scenes: List<Scene> = mutableListOf(
            Scene(project, "scene 1"),
            Scene(project, "scene 2"),
            Scene(project, "scene 3")
        )
    )
}