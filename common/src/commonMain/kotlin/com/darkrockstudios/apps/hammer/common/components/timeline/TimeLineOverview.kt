package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.search.projectMarkdownToPlainText
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView

/** The selected timeline's events, or those in the deleted-timeline bucket. */
interface TimeLineOverview {
	val state: Value<State>

	/** The selected timeline's tags, most used first. */
	val rankedTags: Value<List<TagCount>>

	/** False when the move was refused, as it is on a DATED timeline and in the deleted-timeline bucket. */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean

	/**
	 * Moves every event in the deleted-timeline bucket to [target], undated where its dates do not fit
	 * there. A placement whose subject is already on [target] stays behind. Returns how many moved.
	 */
	suspend fun moveOrphansTo(target: String): Int

	/** Deletes every event in the deleted-timeline bucket, on every synced device. Returns how many. */
	suspend fun deleteOrphans(): Int

	fun setSearchQuery(query: String)
	fun toggleTag(tag: String)
	fun toggleLane(lane: String)

	/** Clears the tag and lane filters; the search stays. */
	fun clearFilters()

	/** Which drawing a DATED timeline shows. */
	fun setView(view: TimelineView)

	/** Whether the drawing, under the current filters and frame, draws [eventId] as a span, and it is not already framed. */
	fun canFrame(eventId: Int): Boolean

	/** Which stretch of a DATED timeline the drawing shows. [TimelineFrame.Story] needs a placed scene. */
	fun setFrame(frame: TimelineFrame)

	/**
	 * The previews read so far, by event id. Each stays until its event's row changes; until one is
	 * here, [TimelineRow.basicPreview] is what is known.
	 */
	val previews: Value<Map<Int, EventPreview>>

	/**
	 * Reads [eventId]'s preview into [previews], unless it is there or being read already. Does
	 * nothing for an event the timeline does not have.
	 */
	fun requestPreview(eventId: Int)

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
		/** As typed. */
		val searchQuery: String = "",
		/** Rows must carry every one of these tags to be listed or drawn. */
		val tagFilter: Set<String> = emptySet(),
		val laneFilter: Set<String> = emptySet(),
		val view: TimelineView = TimelineView.SEQUENCE,
		val frame: TimelineFrame = TimelineFrame.Everything,
		/** Whether some placed scene has a start, which [TimelineFrame.Story] frames. */
		val storyFrameAvailable: Boolean = false,
		/** The live timelines, main first. */
		val timelines: List<TimelineSummary> = emptyList(),
		val selection: TimelineSelection = TimelineSelection.Main,
		/** How many events are in the deleted-timeline bucket. */
		val orphanCount: Int = 0,
	) {
		/** The deleted-timeline bucket is shown: a read-only list, with no drawing, drag or create. */
		val isOrphans: Boolean get() = selection == TimelineSelection.Orphans

		/** The selected timeline; null in the deleted-timeline bucket and until the timelines load. */
		val selectedTimeline: TimelineSummary?
			get() = (selection as? TimelineSelection.Def)?.let { def -> timelines.firstOrNull { it.def.id == def.id } }

		/** What the search and filters leave of [rows], worked out on first read. */
		val filtered: FilteredRows by lazy {
			val filtered = filterRows(rows.orEmpty(), mode, searchQuery, tagFilter, laneFilter)
			if (isOrphans) filtered.copy(reorderable = false) else filtered
		}
	}
}

/** An event as its preview card shows it, on hover or a long press. */
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
) {
	/** [event]'s body as the prose a reader sees, which the search matches. Worked out once, as the row is built. */
	internal val bodyText: String = projectMarkdownToPlainText(event.content)
}

/** Whether the drawing shows this row: it has a start, and is not an archived scene. */
val TimelineRow.isDrawn: Boolean get() = start != null && !subjectArchived

/**
 * Whether the drawing keeps this row under [laneFilter]: an empty filter keeps everything, and scenes
 * and unlaned events always stay, in the ledger.
 */
fun TimelineRow.passesLaneFilter(laneFilter: Set<String>): Boolean {
	val lane = event.lane?.takeIf { it.isNotBlank() }
	return laneFilter.isEmpty() || event.kind == TimelineKind.SCENE || lane == null || lane in laneFilter
}

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
