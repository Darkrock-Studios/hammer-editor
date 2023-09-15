package com.darkrockstudios.apps.hammer.common.data.timelinerepository

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
	val date: String? = null,
	@TomlMultilineString
	val content: String,
)