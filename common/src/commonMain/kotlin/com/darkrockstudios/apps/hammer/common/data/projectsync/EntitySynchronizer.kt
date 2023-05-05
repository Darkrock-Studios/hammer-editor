package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel

typealias EntityConflictHandler<T> = suspend (T) -> Unit

abstract class EntitySynchronizer<T : ApiProjectEntity>(
	protected val projectDef: ProjectDef,
	protected val serverProjectApi: ServerProjectApi
) {
	val conflictResolution = Channel<T>()

	abstract suspend fun prepareForSync()
	abstract suspend fun ownsEntity(id: Int): Boolean
	abstract suspend fun getEntityHash(id: Int): String?
	abstract suspend fun createEntityForId(id: Int): T

	suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<T>,
		onLog: suspend (String?) -> Unit
	): Boolean {
		Napier.d("Uploading Scene $id")

		val entity = createEntityForId(id)
		val result = serverProjectApi.uploadEntity(projectDef, entity, originalHash, syncId)
		return if (result.isSuccess) {
			onLog("Uploaded Scene $id")
			true
		} else {
			val exception = result.exceptionOrNull()
			val conflictException = exception as? EntityConflictException
			if (conflictException != null) {
				onLog("Conflict for scene $id detected")
				onConflict(conflictException.entity as T)

				val resolvedEntity = conflictResolution.receive()
				val resolveResult = serverProjectApi.uploadEntity(projectDef, resolvedEntity, null, syncId, true)

				if (resolveResult.isSuccess) {
					onLog("Resolved conflict for scene $id")
					storeEntity(resolvedEntity, syncId, onLog)
					true
				} else {
					onLog("Scene conflict resolution failed for $id")
					false
				}
			} else {
				onLog("Failed to upload scene $id")
				false
			}
		}
	}

	abstract suspend fun storeEntity(serverEntity: T, syncId: String, onLog: suspend (String?) -> Unit)
	abstract suspend fun reIdEntity(oldId: Int, newId: Int)
	abstract suspend fun finalizeSync()
	abstract fun getEntityType(): EntityType
	abstract suspend fun deleteEntityLocal(id: Int, onLog: suspend (String?) -> Unit)
	abstract suspend fun hashEntities(newIds: List<Int>): Set<EntityHash>
}