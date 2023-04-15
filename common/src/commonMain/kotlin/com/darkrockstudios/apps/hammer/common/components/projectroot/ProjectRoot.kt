package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.components.projecteditor.ProjectEditor
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSyncComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectRoot : AppCloseManager, HammerComponent {
	val routerState: Value<ChildStack<*, Destination<*>>>
	val modalRouterState: Value<ChildSlot<ProjectRootModalRouter.Config, ModalDestination>>
	val shouldConfirmClose: Value<Boolean>
	val backEnabled: Value<Boolean>

	fun showEditor()
	fun showNotes()
	fun showEncyclopedia()
	fun showHome()
	fun showTimeLine()
	fun showDestination(type: DestinationTypes)
	fun isAtRoot(): Boolean

	fun showProjectSync()
	fun dismissProjectSync()

	sealed class Destination<T> : Router {
		abstract val component: T

		override fun isAtRoot(): Boolean {
			return (component as? Router)?.isAtRoot() ?: true
		}

		data class EditorDestination(override val component: ProjectEditor) : Destination<ProjectEditor>()

		data class NotesDestination(override val component: Notes) : Destination<Notes>()

		data class EncyclopediaDestination(override val component: Encyclopedia) : Destination<Encyclopedia>()

		data class TimeLineDestination(override val component: TimeLine) : Destination<TimeLine>()

		data class HomeDestination(override val component: ProjectHome) : Destination<ProjectHome>()

		fun getLocationType(): DestinationTypes {
			return when (this) {
				is EditorDestination -> DestinationTypes.Editor
				is EncyclopediaDestination -> DestinationTypes.Encyclopedia
				is NotesDestination -> DestinationTypes.Notes
				is TimeLineDestination -> DestinationTypes.TimeLine
				is HomeDestination -> DestinationTypes.Home
			}
		}
	}

	sealed class ModalDestination {
		object None : ModalDestination()

		data class ProjectSync(val component: ProjectSyncComponent) : ModalDestination()
	}

	enum class DestinationTypes(val text: String) {
		Home("Home"),
		Editor("Editor"),
		Notes("Notes"),
		Encyclopedia("Encyclopedia"),
		TimeLine("Time Line"),
	}
}