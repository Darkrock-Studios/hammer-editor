package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSceneDraftSynchronizer(
	fileSystem: FileSystem,
	json: Json,
) : ServerEntitySynchronizer<ApiProjectEntity.SceneDraftEntity>(fileSystem, json) {
	override fun hashEntity(entity: ApiProjectEntity.SceneDraftEntity): String {
		return EntityHash.hashSceneDraft(
			id = entity.id,
			created = entity.created,
			name = entity.name,
			content = entity.content,
		)
	}

	override val entityType = ApiProjectEntity.Type.SCENE_DRAFT
	override val entityClazz = ApiProjectEntity.SceneDraftEntity::class
	override val pathStub = ApiProjectEntity.Type.SCENE_DRAFT.name.lowercase()
}