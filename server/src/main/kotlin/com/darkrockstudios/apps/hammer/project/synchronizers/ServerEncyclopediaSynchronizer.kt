package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDatasource

class ServerEncyclopediaSynchronizer(
	datasource: ProjectDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(datasource) {
	override fun hashEntity(entity: ApiProjectEntity.EncyclopediaEntryEntity): String {
		return EntityHasher.hashEncyclopediaEntry(
			id = entity.id,
			name = entity.name,
			entryType = entity.entryType,
			text = entity.text,
			tags = entity.tags,
			image = entity.image,
		)
	}

	override val entityType = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY
	override val entityClazz = ApiProjectEntity.EncyclopediaEntryEntity::class
	override val pathStub = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY.name.lowercase()
}