package com.darkrockstudios.apps.hammer.common.data.tagindex

import kotlinx.serialization.Serializable

@Serializable
enum class TaggedEntityType {
	Encyclopedia,
	Note,
	TimelineEvent,
	Scene,
}
