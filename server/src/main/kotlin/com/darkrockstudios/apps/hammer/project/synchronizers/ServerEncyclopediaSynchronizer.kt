package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource

class ServerEncyclopediaSynchronizer(
	datasource: ProjectEntityDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(datasource) {
	override val entityType = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY
	override val entityClazz = ApiProjectEntity.EncyclopediaEntryEntity::class
	override val pathStub = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY.name.lowercase()
}
