package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import kotlinx.serialization.Serializable

/** Which timeline the timeline screen shows. */
@Serializable
sealed interface TimelineSelection {
	@Serializable
	data class Def(val id: String) : TimelineSelection

	/** The events whose timeline was deleted, such as ones made offline on another device. */
	@Serializable
	data object Orphans : TimelineSelection

	companion object {
		val Main = Def(TimelineDef.MAIN_ID)
	}
}

/** The header switcher's create, rename and delete dialogs; at most one is open. */
data class TimelineSwitcher(
	val dialog: Dialog? = null,
	/** Why the open dialog's last request was refused. */
	val error: TimeLineEventError? = null,
) {
	sealed interface Dialog {
		/** The draft fields, so the dialog can be reopened as it was left. */
		data class Create(
			val name: String = "",
			val mode: TimelineMode = TimelineMode.MANUAL,
			val preset: CalendarPreset = CalendarPreset.STORY,
		) : Dialog

		data class Rename(val timelineId: String, val name: String) : Dialog

		/** [eventCount] events are deleted with the timeline, on every synced device. */
		data class Delete(val timelineId: String, val name: String, val eventCount: Int) : Dialog
	}
}
