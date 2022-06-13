package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.router.RouterState
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.di.HammerComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneList

interface ProjectEditor : HammerComponent {
    val listRouterState: Value<RouterState<*, Child.List>>
    val detailsRouterState: Value<RouterState<*, Child.Detail>>

    data class State(
        val project: Project,
        val isMultiPane: Boolean = false
    )

    val state: Value<State>

    fun setMultiPane(isMultiPane: Boolean)

    fun closeDetails(): Boolean

    sealed class Child {
        sealed class List: Child() {
            class Scenes(val component: SceneList): List()

            object None: List()
        }
        sealed class Detail : Child() {
            class Editor(val component: SceneEditor): Detail()

            object None: Detail()
        }
    }
}