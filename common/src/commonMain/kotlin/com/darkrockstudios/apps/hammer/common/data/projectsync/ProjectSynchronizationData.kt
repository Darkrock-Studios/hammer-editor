package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RenamedProject(
	val projectId: ProjectId,
	val newName: String,
)

@Serializable
data class ProjectsSynchronizationData(
	val deletedProjects: Set<ProjectId>,
	val projectsToDelete: Set<ProjectId>,
	val projectsToRename: Set<RenamedProject>,
	val projectsToCreate: Set<String>,
)

@Serializable
data class ProjectSynchronizationData(
	val currentSyncId: String? = null,
	val lastId: Int,
	val newIds: List<Int>,
	val lastSync: Instant,
	val dirty: List<EntityOriginalState>,
	val deletedIds: Set<Int>,
)

@Serializable
data class EntityOriginalState(
	val id: Int,
	val originalHash: String
)