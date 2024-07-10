package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDatasource

class ServerSceneDraftSynchronizer(
	datasource: ProjectDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.SceneDraftEntity>(datasource) {
	override fun hashEntity(entity: ApiProjectEntity.SceneDraftEntity): String {
		return EntityHasher.hashSceneDraft(
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