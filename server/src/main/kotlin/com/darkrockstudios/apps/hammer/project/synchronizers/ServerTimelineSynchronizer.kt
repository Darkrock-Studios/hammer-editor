package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.ProjectDatasource

class ServerTimelineSynchronizer(
	datasource: ProjectDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.TimelineEventEntity>(datasource) {
	override fun hashEntity(entity: ApiProjectEntity.TimelineEventEntity): String {
		return EntityHasher.hashTimelineEvent(
			id = entity.id,
			order = entity.order,
			date = entity.date,
			content = entity.content
		)
	}

	override val entityType = ApiProjectEntity.Type.TIMELINE_EVENT
	override val entityClazz = ApiProjectEntity.TimelineEventEntity::class
	override val pathStub = ApiProjectEntity.Type.TIMELINE_EVENT.name.lowercase()
}