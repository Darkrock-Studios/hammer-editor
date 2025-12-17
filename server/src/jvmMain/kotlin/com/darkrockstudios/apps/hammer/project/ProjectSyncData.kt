package com.darkrockstudios.apps.hammer.project

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ProjectSyncData(
	val lastSync: Instant = Instant.DISTANT_PAST,
	val lastId: Int,
	val deletedIds: Set<Int>,
)