package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ProjectsSyncData(
	val lastSync: Instant = Instant.DISTANT_PAST,
	val deletedProjects: Set<ProjectId>,
)
