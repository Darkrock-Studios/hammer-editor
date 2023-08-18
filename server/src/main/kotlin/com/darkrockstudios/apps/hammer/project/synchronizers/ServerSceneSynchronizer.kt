package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSceneSynchronizer(
	fileSystem: FileSystem,
	json: Json,
	private val log: io.ktor.util.logging.Logger
) : ServerEntitySynchronizer<ApiProjectEntity.SceneEntity>(fileSystem, json) {

	override fun hashEntity(entity: ApiProjectEntity.SceneEntity): String {
		return EntityHasher.hashScene(
			id = entity.id,
			order = entity.order,
			name = entity.name,
			type = entity.sceneType,
			content = entity.content
		)
	}

	override fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?
	): List<Int> {
		val entities = getEntityDefs(userId, projectDef)

		// Sort by SceneType, we want directories first
		val entityIds = entities.mapNotNull { def ->
			val entityResult = loadEntity(userId, projectDef, def.id)
			if (isSuccess(entityResult)) {
				val entity = entityResult.data
				Pair(def.id, entity.sceneType)
			} else {
				log.error("Failed to get entity $def.id: ${entityResult.error}")
				null
			}
		}
			.sortedByDescending { it.second.ordinal }
			.map { it.first }
			.filter { entityId ->
				if (clientState != null) {
					val entity = loadEntity(userId, projectDef, entityId)
					if(isSuccess(entity)) {
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

	override val entityType = ApiProjectEntity.Type.SCENE
	override val entityClazz = ApiProjectEntity.SceneEntity::class
	override val pathStub = ApiProjectEntity.Type.SCENE.name.lowercase()
}