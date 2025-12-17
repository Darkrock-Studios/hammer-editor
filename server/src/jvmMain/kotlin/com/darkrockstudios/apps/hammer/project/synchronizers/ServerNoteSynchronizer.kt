package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource

class ServerNoteSynchronizer(
	datasource: ProjectEntityDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.NoteEntity>(datasource) {
	override fun hashEntity(entity: ApiProjectEntity.NoteEntity): String {
		return EntityHasher.hashNote(
			id = entity.id,
			created = entity.created,
			content = entity.content,
		)
	}

	override val entityType = ApiProjectEntity.Type.NOTE
	public override val entityClazz = ApiProjectEntity.NoteEntity::class
	public override val pathStub = ApiProjectEntity.Type.NOTE.name.lowercase()
}