package com.darkrockstudios.apps.hammer.common.data.tagindex

data class TagIndex(
	val tagToEntities: Map<String, Set<TaggedEntityRef>>,
	val countsByType: Map<TaggedEntityType, Map<String, Int>>,
) {
	/** Every tag by how many entities carry it, most used first. */
	fun rankedTags(limit: Int = Int.MAX_VALUE): List<TagCount> = tagToEntities.toRankedTagCounts(limit) { it.size }

	/** Tags by how many entities of [type] carry them, most used first. */
	fun rankedTags(type: TaggedEntityType, limit: Int = Int.MAX_VALUE): List<TagCount> =
		countsByType[type].orEmpty().toRankedTagCounts(limit) { it }

	companion object {
		val EMPTY = TagIndex(emptyMap(), emptyMap())
	}
}

internal inline fun <V> Map<String, V>.toRankedTagCounts(
	limit: Int,
	countOf: (V) -> Int,
): List<TagCount> =
	map { TagCount(it.key, countOf(it.value)) }
		.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag })
		.take(limit)
