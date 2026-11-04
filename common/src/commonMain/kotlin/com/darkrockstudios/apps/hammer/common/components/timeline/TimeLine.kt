package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

interface TimeLine : Router, BackHandlerOwner {
	fun onBack()
	val stack: Value<ChildStack<Config, Destination>>

	/** An event opened from the drawing, shown over it as a dialog so the drawing keeps its place. */
	val eventDialog: Value<ChildSlot<EventDialogConfig, ViewTimeLineEvent>>

	/**
	 * The timeline the overview shows. Remembered per project on this device; a selection whose
	 * timeline is deleted, or an empty deleted-timeline bucket, falls back to main.
	 */
	val selection: Value<TimelineSelection>

	val switcher: Value<TimelineSwitcher>

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

		/** Creates on [timelineId]. */
		@Serializable
		data class CreateEventConfig(
			val projectDef: ProjectDef,
			val timelineId: String = TimelineDef.MAIN_ID,
		) : Config()
	}

	@Serializable
	data class EventDialogConfig(val projectDef: ProjectDef, val eventId: Int)

	fun showOverview()
	fun showViewEvent(eventId: Int)
	/** Creates on the selected timeline; nothing opens from the deleted-timeline bucket. */
	fun showCreateEvent()
	fun showEventDialog(eventId: Int)

	/** Closes the event dialog, asking first when it holds unsaved edits. */
	fun closeEventDialog()

	/** Whether the drawing can frame [eventId]'s band: a span, not already framed. */
	fun canFrameEvent(eventId: Int): Boolean

	/** Frames the drawing on [eventId]'s band and closes the event dialog. */
	fun frameEvent(eventId: Int)

	fun selectTimeline(selection: TimelineSelection)

	/** Opens event [eventId] on its own timeline, or in the deleted-timeline bucket for a straggler. */
	fun showEvent(eventId: Int)

	/** Selects [timelineId] and opens its event [eventId], where the current event is open. */
	fun showEventOn(timelineId: String, eventId: Int)

	/** Opens the project settings, where each timeline's mode, calendar and lanes are edited. */
	fun showTimelineSettings()

	fun requestCreateTimeline()

	/** Creates the timeline and selects it; a refusal stays in [switcher] with the dialog open. */
	fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset)
	fun requestRenameTimeline(timelineId: String)
	fun renameTimeline(name: String)

	/** Asks before deleting [timelineId] and its events; main cannot be deleted. */
	fun requestDeleteTimeline(timelineId: String)
	fun confirmDeleteTimeline()
	fun dismissTimelineDialog()
}