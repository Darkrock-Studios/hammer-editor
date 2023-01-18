package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.notes.Notes
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditor

interface ProjectRoot : AppCloseManager, HammerComponent {
    val routerState: Value<ChildStack<*, Destination>>
    val shouldConfirmClose: Value<Boolean>
    val backEnabled: Value<Boolean>

    fun showEditor()
    fun showNotes()
    fun showEncyclopedia()

    sealed class Destination {
        data class EditorDestination(val component: ProjectEditor) : Destination(), Router {
            override fun isAtRoot() = component.isAtRoot()
        }

        data class NotesDestination(val component: Notes) : Destination()

        data class EncyclopediaDestination(val component: Encyclopedia) : Destination(), Router {
            override fun isAtRoot() = component.isAtRoot()
        }
    }
}