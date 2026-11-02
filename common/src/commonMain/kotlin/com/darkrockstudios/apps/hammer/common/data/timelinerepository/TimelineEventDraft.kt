package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind

/** A new timeline event. A null [order] appends it; a null [id] claims a fresh one. */
data class TimelineEventDraft(
	val kind: TimelineKind = TimelineKind.EVENT,
	val subject: Int? = null,
	val title: String? = null,
	val label: String? = null,
	val content: String = "",
	val tags: Set<String> = emptySet(),
	val references: Set<Int> = emptySet(),
	val lane: String? = null,
	val milestone: Boolean = false,
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	val cachedTitle: String? = null,
	val order: Int? = null,
	val id: Int? = null,
)
