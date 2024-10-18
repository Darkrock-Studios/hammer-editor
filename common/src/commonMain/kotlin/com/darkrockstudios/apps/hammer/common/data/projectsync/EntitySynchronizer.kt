package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.requireProjectId
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel

typealias EntityConflictHandler<T> = suspend (T) -> Unit

abstract class EntitySynchronizer<T : ApiProjectEntity>(
	protected val projectDef: ProjectDef,
	protected val serverProjectApi: ServerProjectApi,
	protected val projectMetadataDatasource: ProjectMetadataDatasource,
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
		onLog: OnSyncLog
	): Boolean {
		Napier.d("Uploading Scene $id")

		val serverProjectId = projectMetadataDatasource.requireProjectId(projectDef)

		val entity = createEntityForId(id)
		val result = serverProjectApi.uploadEntity(
			projectDef.name,
			serverProjectId,
			entity,
			originalHash,
			syncId
		)
		return if (result.isSuccess) {
			onLog(syncLogI("Uploaded Scene $id", projectDef))
			true
		} else {
			val exception = result.exceptionOrNull()
			val conflictException = exception as? EntityConflictException
			if (conflictException != null) {
				onLog(syncLogW("Conflict for scene $id detected", projectDef))
				onConflict(conflictException.entity as T)

				val resolvedEntity = conflictResolution.receive()
				val resolveResult = serverProjectApi.uploadEntity(
					projectDef.name,
					serverProjectId,
					resolvedEntity,
					null,
					syncId,
					true
				)

				if (resolveResult.isSuccess) {
					onLog(syncLogI("Resolved conflict for scene $id", projectDef))
					storeEntity(resolvedEntity, syncId, onLog)
					true
				} else {
					onLog(syncLogE("Scene conflict resolution failed for $id", projectDef))
					false
				}
			} else {
				onLog(syncLogE("Failed to upload scene $id", projectDef))
				false
			}
		}
	}

	abstract suspend fun storeEntity(serverEntity: T, syncId: String, onLog: OnSyncLog): Boolean
	abstract suspend fun reIdEntity(oldId: Int, newId: Int)
	abstract suspend fun finalizeSync()
	abstract fun getEntityType(): EntityType
	abstract suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog)
	abstract suspend fun hashEntities(newIds: List<Int>): Set<EntityHash>
}