package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.Before
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
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

	@Before
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
		} returns SResult.success(true)

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
		} returns SResult.success(true)

		val synchronizer = createSynchronizer()
		val result = synchronizer.saveEntity(userId, mockk(), entity, null, false)

		assertTrue(isSuccess(result))
		coVerify { datasource.storeEntity(userId, any(), entity, any(), entityClazz.serializer()) }
	}
}