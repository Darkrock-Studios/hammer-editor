package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSceneSynchronizer(
	fileSystem: FileSystem,
	json: Json,
	private val log: io.ktor.util.logging.Logger
) : ServerEntitySynchronizer<ApiProjectEntity.SceneEntity>(fileSystem, json) {

	override fun hashEntity(entity: ApiProjectEntity.SceneEntity): String {
		return EntityHash.hashScene(
			id = entity.id,
			order = entity.order,
			name = entity.name,
			type = entity.sceneType,
			content = entity.content
		)
	}

	override fun getUpdateSequence(userId: Long, projectDef: ProjectDefinition): List<Int> {
		val entities = getEntityDefs(userId, projectDef)

		// Sort by SceneType, we want directories first
		val entityIds = entities.mapNotNull { def ->
			val entityResult = loadEntity(userId, projectDef, def.id)
			if (entityResult.isSuccess) {
				val entity = entityResult.getOrThrow()
				Pair(def.id, entity.sceneType)
			} else {
				log.error("Failed to get entity $def.id: ${entityResult.exceptionOrNull()?.message}")
				null
			}
		}
			.sortedByDescending { it.second.ordinal }
			.map { it.first }

		return entityIds
	}

	override val entityType = ApiProjectEntity.Type.SCENE
	override val entityClazz = ApiProjectEntity.SceneEntity::class
	override val pathStub = ApiProjectEntity.Type.SCENE.name.lowercase()
}