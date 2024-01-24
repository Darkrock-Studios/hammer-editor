package com.darkrockstudios.apps.hammer.common.components.storyeditor

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverview
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import kotlinx.coroutines.flow.SharedFlow

interface StoryEditor : AppCloseManager, Router, HammerComponent {
	val listRouterState: Value<ChildStack<*, ChildDestination.List>>
	val detailsRouterState: Value<ChildStack<*, ChildDestination.Detail>>
	val dialogState: Value<ChildSlot<*, ChildDestination.DialogDestination>>
	val fullscreenState: Value<ChildSlot<*, ChildDestination.FullScreen>>

	data class State(
		val projectDef: ProjectDef,
		val isMultiPane: Boolean = false
	)

	val state: Value<State>

	val shouldCloseRoot: SharedFlow<Boolean>

	fun isDetailShown(): Boolean

	fun setMultiPane(isMultiPane: Boolean)
	fun closeDetails(): Boolean
	fun enterFocusMode(sceneItem: SceneItem)
	fun exitFocusMode()

	sealed class ChildDestination {
		sealed class List : ChildDestination() {
			data class Scenes(val component: SceneList) : List()
			data object None : List()
		}

		sealed class Detail : ChildDestination() {
			data class EditorDestination(val component: SceneEditor) : Detail()
			data class DraftsDestination(val component: DraftsList) : Detail()
			data class DraftCompareDestination(val component: DraftCompare) : Detail()
			data object None : Detail()
		}

		sealed class DialogDestination : ChildDestination() {
			data class OutlineDestination(val component: OutlineOverview) : DialogDestination()
			data object None : DialogDestination()
		}

		sealed class FullScreen : ChildDestination() {
			data class FocusModeDestination(val component: FocusMode) : FullScreen()
			data object None : FullScreen()
		}
	}

	sealed class DialogConfig : Parcelable {
		@Parcelize
		data object None : DialogConfig()

		@Parcelize
		data object OutlineOverview : DialogConfig()
	}

	sealed class FullScreenConfig : Parcelable {
		@Parcelize
		data object None : FullScreenConfig()

		@Parcelize
		data class FocusMode(val sceneItem: SceneItem) : FullScreenConfig()
	}

	fun showOutlineOverview()
	fun dismissDialog()
}