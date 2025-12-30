package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectStatistics(
	val numberOfScenes: Int,
	val totalWords: Int,
	val wordsByChapter: Map<String, Int>,
	val encyclopediaEntriesByType: Map<String, Int>,
	val isDirty: Boolean = false,
	val lastCalculated: Instant,
)
