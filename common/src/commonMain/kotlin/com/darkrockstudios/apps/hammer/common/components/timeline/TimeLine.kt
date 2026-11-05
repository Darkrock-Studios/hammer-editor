package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

interface TimeLine : Router, BackHandlerOwner {
	fun onBack()
	val stack: Value<ChildStack<Config, Destination>>

	/** An event opened from the drawing, shown over it as a dialog so the drawing keeps its place. */
	val eventDialog: Value<ChildSlot<EventDialogConfig, ViewTimeLineEvent>>

	sealed class Destination {
		data class TimeLineOverviewDestination(val component: TimeLineOverview) : Destination()

		data class ViewEventDestination(val component: ViewTimeLineEvent) : Destination()

		data class CreateEventDestination(val component: CreateTimeLineEvent) : Destination()
	}

	@Serializable
	sealed class Config {
		@Serializable
		data class TimeLineOverviewConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class ViewEventConfig(val projectDef: ProjectDef, val eventId: Int) : Config()

		@Serializable
		data class CreateEventConfig(val projectDef: ProjectDef) : Config()
	}

	@Serializable
	data class EventDialogConfig(val projectDef: ProjectDef, val eventId: Int)

	fun showOverview()
	fun showViewEvent(eventId: Int)
	fun showCreateEvent()
	fun showEventDialog(eventId: Int)

	/** Closes the event dialog, asking first when it holds unsaved edits. */
	fun closeEventDialog()

	/** Whether the drawing can frame [eventId]'s band: a span, not already framed. */
	fun canFrameEvent(eventId: Int): Boolean

	/** Frames the drawing on [eventId]'s band and closes the event dialog. */
	fun frameEvent(eventId: Int)
}