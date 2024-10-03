package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalSerializationApi::class)
abstract class ServerEntitySynchronizerTest<C : ApiProjectEntity, T : ServerEntitySynchronizer<C>>() :
	BaseTest() {

	protected lateinit var datasource: ProjectDatasource

	abstract val entityType: ApiProjectEntity.Type
	abstract val entityClazz: KClass<C>
	abstract val pathStub: String

	abstract fun createSynchronizer(): T
	abstract fun createNewEntity(): C
	abstract fun createExistingEntity(): C

	@BeforeEach
	override fun setup() {
		super.setup()

		datasource = mockk()
	}

	@Test
	fun `Properties Check`() {
		val synchronizer = createSynchronizer()
		assertEquals(entityType, synchronizer.entityType)
		assertEquals(entityClazz, synchronizer.entityClazz)
		assertEquals(pathStub, synchronizer.pathStub)
	}

	@Test
	fun `Save Entity - No Existing Entity`() = runTest {
		val userId = 1L
		val entityId = 1
		val entity = createNewEntity()

		coEvery {
			datasource.loadEntity(any(), any(), any(), any(), entityClazz.serializer())
		} returns SResult.failure(EntityNotFound(entityId))
		coEvery {
			datasource.storeEntity(any(), any(), entity, any(), entityClazz.serializer())
		} returns SResult.success(Unit)

		val synchronizer = createSynchronizer()
		val result = synchronizer.saveEntity(userId, mockk(), entity, "fake-hash", false)

		assertTrue(isSuccess(result))
		coVerify { datasource.storeEntity(userId, any(), entity, any(), entityClazz.serializer()) }
	}

	@Test
	fun `Save Entity - No Original Hash`() = runTest {
		val userId = 1L
		val entity = createNewEntity()
		val existingEntity = createExistingEntity()

		coEvery {
			datasource.loadEntity(any(), any(), any(), any(), entityClazz.serializer())
		} returns SResult.success(existingEntity)
		coEvery {
			datasource.storeEntity(any(), any(), entity, any(), entityClazz.serializer())
		} returns SResult.success(Unit)

		val synchronizer = createSynchronizer()
		val result = synchronizer.saveEntity(userId, mockk(), entity, null, false)

		assertTrue(isSuccess(result))
		coVerify { datasource.storeEntity(userId, any(), entity, any(), entityClazz.serializer()) }
	}

	@Test
	fun `Save Entity - Original Hash Match`() = runTest {
		val userId = 1L
		val existingEntity = createExistingEntity()
		val newEntity = createNewEntity()

		val synchronizer = createSynchronizer()
		val originalHash = synchronizer.hashEntity(existingEntity)

		coEvery {
			datasource.loadEntity(any(), any(), any(), any(), entityClazz.serializer())
		} returns SResult.success(existingEntity)
		coEvery {
			datasource.storeEntity(any(), any(), newEntity, any(), entityClazz.serializer())
		} returns SResult.success(Unit)

		val result = synchronizer.saveEntity(userId, mockk(), newEntity, originalHash, false)

		assertTrue(isSuccess(result))
		coVerify {
			datasource.storeEntity(
				userId,
				any(),
				newEntity,
				any(),
				entityClazz.serializer()
			)
		}
	}

	@Test
	fun `Save Entity - Original Hash Mismatch`() = runTest {
		val userId = 1L
		val existingEntity = createExistingEntity()
		val newEntity = createNewEntity()

		val synchronizer = createSynchronizer()
		val originalHash = "fake-hash"

		coEvery {
			datasource.loadEntity(any(), any(), any(), any(), entityClazz.serializer())
		} returns SResult.success(existingEntity)
		coEvery {
			datasource.storeEntity(any(), any(), newEntity, any(), entityClazz.serializer())
		} returns SResult.success(Unit)

		val result = synchronizer.saveEntity(userId, mockk(), newEntity, originalHash, false)

		assertFalse(isSuccess(result))
		assertIs<EntityConflictException>(result.exception)
		coVerify(exactly = 0) {
			datasource.storeEntity(
				userId,
				any(),
				newEntity,
				any(),
				entityClazz.serializer()
			)
		}
	}

	@Test
	fun `Load Entity`() = runTest {
		val userId = 1L
		val entityId = 1
		val entity = createExistingEntity()

		coEvery {
			datasource.loadEntity(userId, any(), entityId, any(), entityClazz.serializer())
		} returns SResult.success(entity)

		val synchronizer = createSynchronizer()
		val result = synchronizer.loadEntity(userId, mockk(), entityId)

		assertTrue(isSuccess(result))
		assertEquals(entity, result.data)
		coVerify { datasource.loadEntity(userId, any(), entityId, any(), entityClazz.serializer()) }
	}

	@Test
	fun `Delete Entity`() = runTest {
		val userId = 1L
		val entityId = 1

		coEvery {
			datasource.deleteEntity(userId, any(), any(), entityId)
		} returns SResult.success(Unit)

		val synchronizer = createSynchronizer()
		val result = synchronizer.deleteEntity(userId, mockk(), entityId)

		assertTrue(isSuccess(result))
		coVerify { datasource.deleteEntity(userId, any(), any(), entityId) }
	}

	@Test
	fun `Hash Entity`() = runTest {
		val entity = createExistingEntity()
		val synchronizer = createSynchronizer()
		val hash = EntityHasher.hashEntity(entity)
		val result = synchronizer.hashEntity(entity)
		assertEquals(hash, result)
	}

	@Test
	open fun `Get Update Sequence - No Client State`() = runTest {
		val userId = 1L

		val entities = entityDefs().filter { it.type == entityType }

		coEvery {
			datasource.getEntityDefs(userId, any(), any())
		} returns entities

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), null)

		val entityIds = entities.map { it.id }
		assertEquals(entityIds, result)
	}

	private fun entityDefs() = listOf(
		EntityDefinition(1, ApiProjectEntity.Type.SCENE),
		EntityDefinition(2, ApiProjectEntity.Type.SCENE),
		EntityDefinition(3, ApiProjectEntity.Type.SCENE),
		EntityDefinition(4, ApiProjectEntity.Type.NOTE),
		EntityDefinition(5, ApiProjectEntity.Type.SCENE),
		EntityDefinition(6, ApiProjectEntity.Type.NOTE),
		EntityDefinition(7, ApiProjectEntity.Type.NOTE),
		EntityDefinition(8, ApiProjectEntity.Type.TIMELINE_EVENT),
		EntityDefinition(9, ApiProjectEntity.Type.TIMELINE_EVENT),
		EntityDefinition(10, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
		EntityDefinition(11, ApiProjectEntity.Type.TIMELINE_EVENT),
		EntityDefinition(12, ApiProjectEntity.Type.TIMELINE_EVENT),
		EntityDefinition(13, ApiProjectEntity.Type.SCENE_DRAFT),
		EntityDefinition(14, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
		EntityDefinition(15, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
		EntityDefinition(16, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
		EntityDefinition(17, ApiProjectEntity.Type.SCENE_DRAFT),
		EntityDefinition(18, ApiProjectEntity.Type.SCENE_DRAFT),
	)

	private val entities = listOf(
		ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 2,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 2,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		)
	)
}