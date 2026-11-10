package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
			label = "July 4th",
			content = "Test Content",
			order = 1,
			tags = setOf("alpha", "beta"),
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.TimelineEventEntity {
		return ApiProjectEntity.TimelineEventEntity(
			id = 1,
			label = "July 4th",
			content = "Test Content Different",
			order = 3,
			tags = setOf("alpha", "beta"),
		)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Save then load keeps the timeline`() = runTest {
		val entity = createNewEntity().copy(timeline = "a3f09c1e")
		val stored = slot<ApiProjectEntity.TimelineEventEntity>()
		coEvery {
			datasource.loadEntityHash(any(), any(), any())
		} returns SResult.failure(EntityNotFound(entity.id))
		coEvery {
			datasource.storeEntity(any(), any(), capture(stored), any(), entityClazz.serializer())
		} returns SResult.success(Unit)
		coEvery {
			datasource.loadEntity(any(), any(), entity.id, any(), entityClazz.serializer())
		} answers { SResult.success(stored.captured) }

		val synchronizer = createSynchronizer()
		assertTrue(isSuccess(synchronizer.saveEntity(OwnerId.unchecked(1L), mockk(), entity, null, false)))
		val loaded = synchronizer.loadEntity(OwnerId.unchecked(1L), mockk(), entity.id)
		assertTrue(isSuccess(loaded))

		assertEquals("a3f09c1e", loaded.data.timeline)
		assertEquals(entity.hash(), synchronizer.hashEntity(loaded.data))
	}
}
