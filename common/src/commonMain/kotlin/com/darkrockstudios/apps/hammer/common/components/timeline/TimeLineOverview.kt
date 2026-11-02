package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

interface TimeLineOverview {
	val state: Value<State>
	val rankedTags: Value<List<TagCount>>

	/** False when the move was refused, as it is on a DATED timeline. */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean

	fun setLaneFilter(lanes: Set<String>)
	fun toggleLane(lane: String)

	data class State(
		/** Null until the timeline has loaded. */
		val rows: List<TimelineRow>? = null,
		val mode: TimelineMode = TimelineMode.MANUAL,
		/** In lane order; a lane's position here is its colour index. */
		val lanes: List<LaneCount> = emptyList(),
		val laneFilter: Set<String> = emptySet(),
	)
}

/** One event as the list shows it, in display order. */
data class TimelineRow(
	val event: TimeLineEvent,
	/** The subject's name for a placement, else the event's own title or first line. */
	val title: String,
	/** A placed scene's chapter path; null for anything else. */
	val subtitle: String?,
	/** The label in MANUAL mode; in DATED mode the start date when there is no label. Blank when undated. */
	val whenText: String,
	/** Position of the event's lane in [TimeLineOverview.State.lanes]; null when it has none. */
	val laneIndex: Int?,
	/** A placement whose scene or entry is gone; [title] is then its cached title. */
	val subjectMissing: Boolean = false,
)

data class LaneCount(val lane: String, val count: Int)
