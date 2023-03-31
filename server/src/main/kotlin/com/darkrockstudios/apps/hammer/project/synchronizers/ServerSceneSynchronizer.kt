package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSceneSynchronizer(
	fileSystem: FileSystem,
	json: Json,
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

	override val entityClazz = ApiProjectEntity.SceneEntity::class
	override val pathStub = "scene"
}