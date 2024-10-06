package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlin.system.measureTimeMillis

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
		val entities = datasource.getEntityDefsByType(userId, projectDef, entityType)

		// Sort by SceneType, we want directories first
		val entityIds: List<Int>
		val ms = measureTimeMillis {
			entityIds = entities
				.filter { entifyDef ->
					val clientEntityState = clientState?.entities?.find { it.id == entifyDef.id }
					if (clientEntityState != null) {
						val hashResult = datasource.loadEntityHash(userId, projectDef, entifyDef.id)
						if (isSuccess(hashResult)) {
							val serverHash = hashResult.data
							clientEntityState.hash != serverHash
						} else {
							true
						}
					} else {
						true
					}
				}
				.mapNotNull { entity ->
					val result = loadEntity(userId, projectDef, entity.id)
					if (isSuccess(result)) {
						result.data
					} else {
						null
					}
				}
				.sortedBy { it.id }
				.sortedByDescending { it.sceneType.ordinal }
				.map { it.id }
		}
		//println("------- getUpdateSequence: $ms ms")

		return entityIds
	}
}