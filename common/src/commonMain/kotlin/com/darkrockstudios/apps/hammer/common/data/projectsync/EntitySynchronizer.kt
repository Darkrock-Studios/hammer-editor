package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

typealias EntityConflictHandler<T> = suspend (T) -> Unit

interface EntitySynchronizer<T : ApiProjectEntity> {
	suspend fun prepareForSync()
	suspend fun ownsEntity(id: Int): Boolean
	suspend fun getEntityHash(id: Int): String?
	suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<T>,
		onLog: suspend (String?) -> Unit
	): Boolean

	suspend fun storeEntity(serverEntity: T, syncId: String, onLog: suspend (String?) -> Unit)
	suspend fun reIdEntity(oldId: Int, newId: Int)
	suspend fun finalizeSync()
}