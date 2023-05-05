package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerEncyclopediaSynchronizer(
	fileSystem: FileSystem,
	json: Json,
) : ServerEntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(fileSystem, json) {
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