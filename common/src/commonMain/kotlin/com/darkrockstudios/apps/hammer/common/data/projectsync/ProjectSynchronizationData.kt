package com.darkrockstudios.apps.hammer.common.data.projectsync

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectsSynchronizationData(
	val deletedProjects: Set<String>,
	val projectsToDelete: Set<String>,
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