package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerNoteSynchronizer(
	fileSystem: FileSystem,
	json: Json,
) : ServerEntitySynchronizer<ApiProjectEntity.NoteEntity>(fileSystem, json) {
	override fun hashEntity(entity: ApiProjectEntity.NoteEntity): String {
		return EntityHash.hashNote(
			id = entity.id,
			created = entity.created,
			content = entity.content,
		)
	}

	override val entityClazz = ApiProjectEntity.NoteEntity::class
	override val pathStub = ApiProjectEntity.Type.NOTE.name.lowercase()
}