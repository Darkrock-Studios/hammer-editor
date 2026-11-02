package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString

@Serializable
data class TimeLineContainer(
	val events: List<TimeLineEvent>
)

@Serializable
data class TimeLineEvent(
	val id: Int,
	val order: Int,
	val kind: TimelineKind = TimelineKind.EVENT,
	/** The placed scene or entry; null for [TimelineKind.EVENT]. */
	val subject: Int? = null,
	val title: String? = null,
	/** Free-text "when" shown in manual mode. */
	val label: String? = null,
	@TomlMultilineString
	val content: String = "",
	val tags: Set<String> = emptySet(),
	val references: Set<Int> = emptySet(),
	val lane: String? = null,
	val milestone: Boolean = false,
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	/** The subject's title when last seen, kept so a placement outliving its subject has a name. */
	val cachedTitle: String? = null,
)
