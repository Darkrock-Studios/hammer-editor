package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString

@Serializable
data class TimeLineContainer(
	val events: List<TimeLineEvent>
)

/** Defaulted fields are left out of the file so a plain event stays as small as a legacy one. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TimeLineEvent(
	val id: Int,
	val order: Int,
	@EncodeDefault(EncodeDefault.Mode.NEVER)
	val kind: TimelineKind = TimelineKind.EVENT,
	/** The placed scene or entry; null for [TimelineKind.EVENT]. */
	val subject: Int? = null,
	val title: String? = null,
	/** Free-text "when" shown in manual mode. */
	val label: String? = null,
	@TomlMultilineString
	val content: String = "",
	@EncodeDefault(EncodeDefault.Mode.NEVER)
	val tags: Set<String> = emptySet(),
	@EncodeDefault(EncodeDefault.Mode.NEVER)
	val references: Set<Int> = emptySet(),
	val lane: String? = null,
	@EncodeDefault(EncodeDefault.Mode.NEVER)
	val milestone: Boolean = false,
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	/** The subject's title when last seen, kept so a placement outliving its subject has a name. */
	val cachedTitle: String? = null,
	/** Id of the timeline holding this event; null means main, which writers never spell out. */
	val timeline: String? = null,
)

/** The id of the timeline holding this event, with main spelled out. */
val TimeLineEvent.timelineId: String
	get() = timeline ?: TimelineDef.MAIN_ID
