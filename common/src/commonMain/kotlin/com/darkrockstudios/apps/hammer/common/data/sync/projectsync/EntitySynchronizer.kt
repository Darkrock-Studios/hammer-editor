package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
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
		onLog: OnSyncLog,
		force: Boolean = false,
		// Invoked with the hash the server now holds when an upload is accepted, so the caller can
		// lock it in as the conflict baseline. The hash of the exact entity we sent, captured here
		// rather than re-derived later (local state may have already drifted).
		onSynced: suspend (id: Int, hash: String) -> Unit = { _, _ -> },
	): Boolean {
		Napier.d("Uploading Scene $id")

		val serverProjectId = projectMetadataDatasource.requireProjectId(projectDef)

		val entity = createEntityForId(id)
		val result = serverProjectApi.uploadEntity(
			serverProjectId,
			entity,
			originalHash,
			syncId,
			force
		)
		return if (result.isSuccess) {
			onLog(syncLogI("Uploaded Scene $id", projectDef))
			onSynced(id, entity.hash())
			true
		} else {
			val exception = result.exceptionOrNull()
			val conflictException = exception as? EntityConflictException
			if (conflictException != null) {
				onLog(syncLogW("Conflict for scene $id detected", projectDef))
				onConflict(conflictException.entity as T)

				val resolvedEntity = conflictResolution.receive()
				val resolveResult = serverProjectApi.uploadEntity(
					serverProjectId,
					resolvedEntity,
					null,
					syncId,
					true
				)

				if (resolveResult.isSuccess) {
					onLog(syncLogI("Resolved conflict for scene $id", projectDef))
					storeEntity(resolvedEntity, syncId, onLog)
					onSynced(id, resolvedEntity.hash())
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