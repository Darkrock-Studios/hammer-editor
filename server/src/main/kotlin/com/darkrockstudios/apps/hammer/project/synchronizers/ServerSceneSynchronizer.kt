package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.isSuccess

class ServerSceneSynchronizer(
	datasource: ProjectDatasource,
	private val log: io.ktor.util.logging.Logger
) : ServerEntitySynchronizer<ApiProjectEntity.SceneEntity>(datasource) {

	override val entityType = ApiProjectEntity.Type.SCENE
	override val entityClazz = ApiProjectEntity.SceneEntity::class
	override val pathStub = ApiProjectEntity.Type.SCENE.name.lowercase()

	override fun hashEntity(entity: ApiProjectEntity.SceneEntity): String {
		return EntityHasher.hashScene(
			id = entity.id,
			order = entity.order,
			path = entity.path,
			name = entity.name,
			type = entity.sceneType,
			content = entity.content,
			outline = entity.outline,
			notes = entity.notes,
		)
	}

	override suspend fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?
	): List<Int> {
		val entities = datasource.getEntityDefs(userId, projectDef) { it.type == entityType }

		// Sort by SceneType, we want directories first
		val allEntities = entities.mapNotNull { def ->
			val entityResult = loadEntity(userId, projectDef, def.id)
			if (isSuccess(entityResult)) {
				val entity = entityResult.data
				Pair(def.id, entity.sceneType)
			} else {
				log.error("Failed to get entity $def.id: ${entityResult.error}")
				null
			}
		}
		val entityIds = allEntities.sortedByDescending { it.second.ordinal }
			.map { it.first }
			.filter { entityId ->
				if (clientState != null) {
					val entity = loadEntity(userId, projectDef, entityId)
					if (isSuccess(entity)) {
						val hash = hashEntity(entity.data)
						val clientEntityState = clientState.entities.find { it.id == entityId }
						clientEntityState?.hash != hash
					} else {
						true
					}
				} else {
					true
				}
			}

		return entityIds
	}
}