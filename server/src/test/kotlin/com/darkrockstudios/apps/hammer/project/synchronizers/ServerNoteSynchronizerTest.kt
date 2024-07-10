package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.Before
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerNoteSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.NoteEntity, ServerNoteSynchronizer>() {

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.NOTE
	override val entityClazz: KClass<ApiProjectEntity.NoteEntity> =
		ApiProjectEntity.NoteEntity::class
	override val pathStub: String = "note"

	@Before
	override fun setup() {
		super.setup()
	}

	override fun createSynchronizer(): ServerNoteSynchronizer {
		return ServerNoteSynchronizer(datasource)
	}

	override fun createNewEntity(): ApiProjectEntity.NoteEntity {
		return ApiProjectEntity.NoteEntity(
			id = 1,
			content = "Test Content",
			created = Instant.fromEpochSeconds(123),
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.NoteEntity {
		return ApiProjectEntity.NoteEntity(
			id = 1,
			content = "Test Content Different",
			created = Instant.fromEpochSeconds(123),
		)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Get Note Update Sequence - All the same`() = runTest {
		val userId = 1L

		val entityDefList = entityDefs()

		coEvery {
			datasource.getEntityDefs(userId, any(), any())
		} returns entityDefList

		val entityIdSlot = slot<Int>()
		coEvery {
			datasource.loadEntity(
				userId,
				any(),
				capture(entityIdSlot),
				any(),
				entityClazz.serializer()
			)
		} answers {
			return@answers SResult.success(entities[entityIdSlot.captured - 1])
		}

		val clientState = ClientEntityState(
			entities = setOf(
				EntityHash(1, EntityHasher.hashEntity(entities[0])),
				EntityHash(2, EntityHasher.hashEntity(entities[1])),
				EntityHash(3, EntityHasher.hashEntity(entities[2])),
			)
		)

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), clientState)

		assertEquals(emptyList(), result)
	}

	private val entities = listOf(
		ApiProjectEntity.NoteEntity(
			id = 1,
			content = "Test Content 1",
			created = Instant.fromEpochSeconds(1)
		),
		ApiProjectEntity.NoteEntity(
			id = 2,
			content = "Test Content 1",
			created = Instant.fromEpochSeconds(12)
		),
		ApiProjectEntity.NoteEntity(
			id = 3,
			content = "Test Content 2",
			created = Instant.fromEpochSeconds(123)
		)
	)

	private fun entityDefs() = listOf(
		EntityDefinition(1, ApiProjectEntity.Type.NOTE),
		EntityDefinition(2, ApiProjectEntity.Type.NOTE),
		EntityDefinition(3, ApiProjectEntity.Type.NOTE),
	)
}