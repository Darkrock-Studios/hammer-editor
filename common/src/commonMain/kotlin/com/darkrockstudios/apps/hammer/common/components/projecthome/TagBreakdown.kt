package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import kotlinx.serialization.Serializable

@Serializable
data class TagBreakdown(
	val name: String,
	val countByType: Map<TaggedEntityType, Int>,
) {
	val total: Int get() = countByType.values.sum()
	val breadth: Int get() = countByType.count { (_, v) -> v > 0 }
	fun getCount(type: TaggedEntityType): Int = countByType[type] ?: 0
}

fun TagIndex.toBreakdowns(): List<TagBreakdown> {
	val perTag = mutableMapOf<String, MutableMap<TaggedEntityType, Int>>()
	for ((type, counts) in countsByType) {
		for ((tag, count) in counts) {
			perTag.getOrPut(tag) { mutableMapOf() }[type] = count
		}
	}
	return perTag
		.map { (name, counts) -> TagBreakdown(name, counts.toMap()) }
		.sortedByDescending { it.total }
}

fun TagIndex.totalUsesByType(): Map<TaggedEntityType, Int> =
	countsByType.mapValues { (_, counts) -> counts.values.sum() }
