package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectPlacement
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.hasValidStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One placement of a scene or entry, as the subject's Timelines row links to it. */
data class TimelinePlacementLink(
	val eventId: Int,
	/** The live timeline's name, or null for a straggler on a deleted timeline. */
	val timelineName: String?,
	/** Set only when the timeline is dated and the start fits its calendar, as the timeline's rows show it. */
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	val calendar: CalendarDef? = null,
	/** Shown when there is no [start]. */
	val label: String? = null,
)

internal fun SubjectPlacement.toLink(): TimelinePlacementLink {
	val calendar = def?.takeIf { it.mode == TimelineMode.DATED }?.let { it.calendar ?: CalendarDef() }
	val start = event.start?.takeIf { calendar != null && hasValidStart(event, calendar) }
	return TimelinePlacementLink(
		eventId = event.id,
		timelineName = def?.name,
		start = start,
		end = event.end.takeIf { start != null },
		calendar = calendar.takeIf { start != null },
		label = event.label?.trim()?.ifBlank { null },
	)
}

/** Every [kind] placement of [subjectId] as links, in [TimelineService.placementsFlow] order. */
internal fun TimelineService.placementLinks(kind: TimelineKind, subjectId: Int): Flow<List<TimelinePlacementLink>> =
	placementsFlow(kind, subjectId).map { placements -> placements.map { it.toLink() } }.distinctUntilChanged()
