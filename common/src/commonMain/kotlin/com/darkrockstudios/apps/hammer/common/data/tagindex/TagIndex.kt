package com.darkrockstudios.apps.hammer.common.data.tagindex

data class TagIndex(
	val tagToEntities: Map<String, Set<TaggedEntityRef>>,
	val countsByType: Map<TaggedEntityType, Map<String, Int>>,
) {
	companion object {
		val EMPTY = TagIndex(emptyMap(), emptyMap())
	}
}
