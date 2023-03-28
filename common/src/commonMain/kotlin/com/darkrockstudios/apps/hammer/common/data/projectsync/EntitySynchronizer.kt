package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

typealias EntityConflictHandler<T> = suspend (T) -> Unit

interface EntitySynchronizer<T : ApiProjectEntity> {
	suspend fun uploadEntity(id: Int, syncId: String, onConflict: EntityConflictHandler<T>)
	suspend fun downloadEntity(id: Int, syncId: String)
	suspend fun reIdEntity(oldId: Int, newId: Int)
	suspend fun finalizeSync()
}