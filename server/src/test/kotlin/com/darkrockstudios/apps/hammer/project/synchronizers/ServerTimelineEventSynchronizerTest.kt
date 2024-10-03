package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass

class ServerTimelineEventSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.TimelineEventEntity, ServerTimelineSynchronizer>() {

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.TIMELINE_EVENT
	override val entityClazz: KClass<ApiProjectEntity.TimelineEventEntity> =
		ApiProjectEntity.TimelineEventEntity::class
	override val pathStub: String = "timeline_event"

	@BeforeEach
	override fun setup() {
		super.setup()
	}

	override fun createSynchronizer(): ServerTimelineSynchronizer {
		return ServerTimelineSynchronizer(datasource)
	}

	override fun createNewEntity(): ApiProjectEntity.TimelineEventEntity {
		return ApiProjectEntity.TimelineEventEntity(
			id = 1,
			date = "July 4th",
			content = "Test Content",
			order = 1,
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.TimelineEventEntity {
		return ApiProjectEntity.TimelineEventEntity(
			id = 1,
			date = "July 4th",
			content = "Test Content Different",
			order = 3,
		)
	}
}