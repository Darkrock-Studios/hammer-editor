package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@OptIn(InternalSerializationApi::class)
abstract class ServerEntitySynchronizer<T : ApiProjectEntity>(
	protected val datasource: ProjectDatasource
) {
	abstract val entityType: ApiProjectEntity.Type
	abstract fun hashEntity(entity: T): String
	abstract val entityClazz: KClass<T>
	abstract val pathStub: String

	private suspend fun hashEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int
	): String? {
		val existingEntityResult =
			datasource.loadEntity(
				userId,
				projectDef,
				entityId,
				entityType,
				entityClazz.serializer()
			)

		return if (isSuccess(existingEntityResult)) {
			val existingEntity = existingEntityResult.data
			hashEntity(existingEntity)
		} else {
			null
		}
	}

	private suspend fun checkForConflict(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		originalHash: String?,
		force: Boolean,
	): EntityConflictException? {
		if (force) return null

		val existingEntityResult = datasource.loadEntity(
			userId,
			projectDef,
			entity.id,
			entityType,
			entityClazz.serializer()
		)

		return if (isSuccess(existingEntityResult)) {
			val existingEntity = existingEntityResult.data
			val existingHash = hashEntity(existingEntity)
			if (originalHash != null && existingHash != originalHash) {
				EntityConflictException.fromEntity(existingEntity)
			} else {
				null
			}
		} else {
			null
		}
	}

	@OptIn(InternalSerializationApi::class)
	suspend fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		originalHash: String?,
		force: Boolean,
	): SResult<Unit> {
		val conflict = checkForConflict(
			userId,
			projectDef,
			entity,
			originalHash,
			force,
		)
		return if (conflict == null) {
			datasource.storeEntity(userId, projectDef, entity, entityType, entityClazz.serializer())
		} else {
			SResult.failure(conflict)
		}
	}

	@OptIn(InternalSerializationApi::class)
	suspend fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
	): SResult<T> =
		datasource.loadEntity(userId, projectDef, entityId, entityType, entityClazz.serializer())

	suspend fun deleteEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit> {
		return datasource.deleteEntity(userId, entityType, projectDef, entityId)
	}

	open suspend fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?
	): List<Int> {
		val entities = datasource.getEntityDefs(userId, projectDef) { it.type == entityType }
			.filter { def ->
				val clientEntityState = clientState?.entities?.find { it.id == def.id }
				if (clientEntityState != null) {
					val serverHash = hashEntity(userId, projectDef, def.id)
					clientEntityState.hash != serverHash
				} else {
					true
				}
			}

		return entities.map { it.id }
	}
}
