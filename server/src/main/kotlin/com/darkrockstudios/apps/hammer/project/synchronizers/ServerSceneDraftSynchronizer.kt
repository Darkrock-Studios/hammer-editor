package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource

class ServerSceneDraftSynchronizer(
	datasource: ProjectEntityDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.SceneDraftEntity>(datasource) {
	override val entityType = ApiProjectEntity.Type.SCENE_DRAFT
	override val entityClazz = ApiProjectEntity.SceneDraftEntity::class
	override val pathStub = ApiProjectEntity.Type.SCENE_DRAFT.name.lowercase()
}
