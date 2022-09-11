package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.di.HammerComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneList

interface ProjectEditor : HammerComponent {
    val listRouterState: Value<ChildStack<*, Child.List>>
    val detailsRouterState: Value<ChildStack<*, Child.Detail>>

    data class State(
        val projectDef: ProjectDef,
        val isMultiPane: Boolean = false
    )

    fun isDetailShown(): Boolean

    val state: Value<State>

    val shouldConfirmClose: Value<Boolean>

    fun setMultiPane(isMultiPane: Boolean)
    fun closeDetails(): Boolean

    fun hasUnsavedBuffers(): Boolean
    fun storeDirtyBuffers()

    sealed class Child {
        sealed class List : Child() {
            class Scenes(val component: SceneList) : List()

            object None : List()
        }

        sealed class Detail : Child() {
            class Editor(val component: SceneEditor) : Detail()

            object None : Detail()
        }
    }
}