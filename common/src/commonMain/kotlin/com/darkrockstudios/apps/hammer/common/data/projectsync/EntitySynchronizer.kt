package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

typealias EntityConflictHandler<T> = suspend (T) -> Unit

interface EntitySynchronizer<T : ApiProjectEntity> {
	fun ownsEntity(id: Int): Boolean
	suspend fun getEntityHash(id: Int): String?
	suspend fun uploadEntity(id: Int, syncId: String, onConflict: EntityConflictHandler<T>)
	suspend fun storeEntity(serverEntity: ApiProjectEntity, syncId: String)
	suspend fun reIdEntity(oldId: Int, newId: Int)
	suspend fun finalizeSync()
}