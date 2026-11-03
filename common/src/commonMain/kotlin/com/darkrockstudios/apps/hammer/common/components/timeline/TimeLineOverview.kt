package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView

interface TimeLineOverview {
	val state: Value<State>
	val rankedTags: Value<List<TagCount>>

	/** False when the move was refused, as it is on a DATED timeline. */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean

	fun setLaneFilter(lanes: Set<String>)
	fun toggleLane(lane: String)

	/** Which drawing a DATED timeline shows. */
	fun setView(view: TimelineView)

	/** Selects [eventId] on the drawing; null, or the event already selected, clears the selection. */
	fun select(eventId: Int?)

	/** Which stretch of a DATED timeline the drawing shows. [TimelineFrame.Story] needs a placed scene. */
	fun setFrame(frame: TimelineFrame)

	/** Shows a referenced scene or entry from an event's preview. */
	fun openReference(item: CatalogItem)

	/**
	 * [eventId]'s preview, read once and then served from a cache until the event or its row changes.
	 * Null when the timeline has no such event.
	 */
	suspend fun preview(eventId: Int): EventPreview?

	/** Set by the UI as the reader scrolls the list or drawing down, cleared as they scroll back up. */
	fun setScrolledDown(scrolledDown: Boolean)

	data class State(
		/** Null until the timeline has loaded. */
		val rows: List<TimelineRow>? = null,
		val mode: TimelineMode = TimelineMode.MANUAL,
		/** The timeline's calendar; null until a timeline has had one. */
		val calendar: CalendarDef? = null,
		/** On a DATED timeline, the events without a valid start, in display order. Only legacy data has any. */
		val undatedIds: List<Int> = emptyList(),
		/** In lane order; a lane's position here is its colour index. */
		val lanes: List<LaneCount> = emptyList(),
		val laneFilter: Set<String> = emptySet(),
		val view: TimelineView = TimelineView.SEQUENCE,
		/** The event the drawing projects across its lanes. Not saved. */
		val selectedId: Int? = null,
		/** What the strip under the drawing shows for [selectedId]; null until it has been read. */
		val selectionPreview: EventPreview? = null,
		val frame: TimelineFrame = TimelineFrame.Everything,
		/** Whether some placed scene has a start, which [TimelineFrame.Story] frames. */
		val storyFrameAvailable: Boolean = false,
		/** The reader last scrolled down; the create button stays out of the way until they scroll up. */
		val scrolledDown: Boolean = false,
	)
}

/** An event as its hover card and the selection strip show it. */
data class EventPreview(
	val eventId: Int,
	val kind: TimelineKind,
	val title: String,
	/** The event's label, shown when there is no [start]. Blank when it has none. */
	val whenText: String,
	/** As [TimelineRow.start], [TimelineRow.end] and [TimelineRow.calendar]: the UI formats them. */
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	val calendar: CalendarDef? = null,
	val lane: String? = null,
	/** Plain text: a plain event's own content, else its scene's or entry's opening. Null when empty. */
	val excerpt: String? = null,
	/** A placed entry's primary image. */
	val imageId: Int? = null,
	/** A plain event's references or a placed scene's cast: people, places, other entries, then scenes. */
	val references: List<CatalogItem> = emptyList(),
	val subjectState: SubjectState = SubjectState.Present,
) {
	/** Whether a placement's scene or entry is still there; a plain event's is always [Present]. */
	enum class SubjectState { Present, Missing, Archived }
}

/** One event as the list shows it, in display order. */
data class TimelineRow(
	val event: TimeLineEvent,
	/** The subject's name for a placement, else the event's own title or first line. */
	val title: String,
	/** A placed scene's chapter path; null for anything else. */
	val subtitle: String?,
	/** The event's label, shown when there is no [start]. Blank when it has none. */
	val whenText: String,
	/** Position of the event's lane in [TimeLineOverview.State.lanes]; null when it has none. */
	val laneIndex: Int?,
	/** A placement whose scene or entry is gone; [title] is then its cached title. */
	val subjectMissing: Boolean = false,
	/** A placed scene that is archived; it is listed but not drawn until the scene is unarchived. */
	val subjectArchived: Boolean = false,
	/** On a DATED timeline, a start that fits [calendar]; null otherwise. The UI formats it with [end]. */
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	val calendar: CalendarDef? = null,
)

/** What the row alone says of its event: everything but the excerpt, image and references. */
fun TimelineRow.basicPreview(): EventPreview = EventPreview(
	eventId = event.id,
	kind = event.kind,
	title = title,
	whenText = whenText,
	start = start,
	end = end,
	calendar = calendar,
	lane = event.lane?.takeIf { it.isNotBlank() },
	subjectState = when {
		subjectMissing -> EventPreview.SubjectState.Missing
		subjectArchived -> EventPreview.SubjectState.Archived
		else -> EventPreview.SubjectState.Present
	},
)

data class LaneCount(val lane: String, val count: Int)
