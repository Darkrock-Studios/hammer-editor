package com.darkrockstudios.apps.hammer.common.data.projectsync

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SynchronizationData(
	val currentSyncId: String? = null,
	val lastId: Int,
	val newIds: List<Int>,
	val lastSync: Instant,
	val dirty: List<EntityState>,
	val newlyDeletedIds: Set<Int>,
)

@Serializable
data class EntityState(
	val id: Int,
	val originalHash: String
)