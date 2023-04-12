package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerTimelineSynchronizer(
	fileSystem: FileSystem,
	json: Json,
) : ServerEntitySynchronizer<ApiProjectEntity.TimelineEventEntity>(fileSystem, json) {
	override fun hashEntity(entity: ApiProjectEntity.TimelineEventEntity): String {
		return EntityHash.hashTimelineEvent(
			id = entity.id,
			order = entity.order,
			date = entity.date,
			content = entity.content
		)
	}

	override val entityClazz = ApiProjectEntity.TimelineEventEntity::class
	override val pathStub = ApiProjectEntity.Type.TIMELINE_EVENT.name.lowercase()
}