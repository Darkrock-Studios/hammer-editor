package com.darkrockstudios.apps.hammer.base.timeline

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [code] is hashed; never reuse or renumber one. */
@Serializable
enum class TimelineKind(val code: Int) {
	/** Authored on the timeline: title, content, tags and references of its own. */
	@SerialName("event")
	EVENT(0),

	/** Places an encyclopedia entry of type EVENT on the timeline. */
	@SerialName("entry")
	ENTRY(1),

	/** Places a scene on the timeline. */
	@SerialName("scene")
	SCENE(2);

	/** A placement carries only placement data; its words come from its subject. */
	val isPlacement: Boolean get() = this != EVENT
}
