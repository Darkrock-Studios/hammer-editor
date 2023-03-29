package com.darkrockstudios.apps.hammer.project

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectSyncData(
	val lastSync: Instant = Instant.DISTANT_PAST,
	val lastId: Int
)