package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.projectroot.Router
import kotlinx.coroutines.flow.SharedFlow

interface ProjectEditor : AppCloseManager, Router, HammerComponent {
    val listRouterState: Value<ChildStack<*, ChildDestination.List>>
    val detailsRouterState: Value<ChildStack<*, ChildDestination.Detail>>

    data class State(
        val projectDef: ProjectDef,
        val isMultiPane: Boolean = false
    )

    val state: Value<State>

    val shouldCloseRoot: SharedFlow<Boolean>

    fun isDetailShown(): Boolean

    fun setMultiPane(isMultiPane: Boolean)
    fun closeDetails(): Boolean

    sealed class ChildDestination {
        sealed class List : ChildDestination() {
            data class Scenes(val component: SceneList) : List()

            object None : List()
        }

        sealed class Detail : ChildDestination() {
            data class EditorDestination(val component: SceneEditor) : Detail()

            data class DraftsDestination(val component: DraftsList) : Detail()

            object None : Detail()
        }
    }
}