package com.darkrockstudios.apps.hammer.projects

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectsSyncData(
	val lastSync: Instant = Instant.DISTANT_PAST,
	val deletedProjects: Set<String>,
)
