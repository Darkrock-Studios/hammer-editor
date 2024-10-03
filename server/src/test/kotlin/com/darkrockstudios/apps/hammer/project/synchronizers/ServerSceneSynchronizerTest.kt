package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
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
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class ServerSceneSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.SceneEntity, ServerSceneSynchronizer>() {

	private lateinit var log: io.ktor.util.logging.Logger

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.SCENE
	override val entityClazz: KClass<ApiProjectEntity.SceneEntity> =
		ApiProjectEntity.SceneEntity::class
	override val pathStub: String = "scene"

	@BeforeEach
	override fun setup() {
		super.setup()
		log = mockk()
	}

	override fun createSynchronizer(): ServerSceneSynchronizer {
		return ServerSceneSynchronizer(datasource, log)
	}

	override fun createNewEntity(): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 2,
			name = "Test Scene Different",
			path = listOf(0),
			content = "Test Content Different",
			outline = "Test Outline Different",
			notes = "Test Notes Different",
		)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	override fun `Get Update Sequence - No Client State`() = runTest {
		val entityIdSlot = slot<Int>()
		coEvery {
			datasource.loadEntity(
				any(),
				any(),
				capture(entityIdSlot),
				any(),
				entityClazz.serializer()
			)
		} answers {
			return@answers SResult.success(entities1[entityIdSlot.captured - 1])
		}

		val userId = 1L

		val entities = entityDefs(entities1.size).filter { it.type == entityType }

		coEvery {
			datasource.getEntityDefs(userId, any(), any())
		} returns entities

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), null)

		val entityIds = entities.map { it.id }
		assertEquals(entityIds, result)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Get Scene Update Sequence - All the same`() = runTest {
		val userId = 1L

		val entityDefList = entityDefs(entities1.size)

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
			return@answers SResult.success(entities1[entityIdSlot.captured - 1])
		}

		val clientState = ClientEntityState(
			entities = List(entities1.size) {
				EntityHash(it + 1, EntityHasher.hashEntity(entities1[it]))
			}.toSet()
		)

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), clientState)

		assertEquals(emptyList(), result)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Get Scene Update Sequence - Reorder Groups`() = runTest {
		val userId = 1L

		val entityDefList = entityDefs(entities2.size)

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
			return@answers SResult.success(entities2[entityIdSlot.captured - 1])
		}

		val clientState = ClientEntityState(
			entities = emptySet()
		)

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), clientState)

		assertEquals(listOf(3, 1, 2, 4), result)
	}

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Get Scene Update Sequence - All different`() = runTest {
		val userId = 1L

		val entityDefList = entityDefs(entities1.size)

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
			return@answers SResult.success(entities1[entityIdSlot.captured - 1])
		}

		val clientState = ClientEntityState(
			entities = List(entities1.size) {
				val e = entities1[it]
				val different = e.copy(name = e.name + " Different")
				EntityHash(it, EntityHasher.hashEntity(different))
			}.toSet()
		)

		val synchronizer = createSynchronizer()
		val result = synchronizer.getUpdateSequence(userId, mockk(), clientState)

		assertEquals(entities1.map { it.id }, result)
	}

	private val entities1 = listOf(
		ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene 1",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 2,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene 2",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 3,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene 3",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		)
	)

	private val entities2 = listOf(
		ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene 1",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 4,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene 2",
			path = listOf(0, 2),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 3,
			sceneType = ApiSceneType.Group,
			order = 1,
			name = "Test Scene 3",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
		ApiProjectEntity.SceneEntity(
			id = 2,
			sceneType = ApiSceneType.Scene,
			order = 2,
			name = "Test Scene 4",
			path = listOf(0),
			content = "Test Content",
			outline = "Test Outline",
			notes = "Test Notes",
		),
	)

	private fun entityDefs(n: Int) = List(n) {
		EntityDefinition(it + 1, ApiProjectEntity.Type.SCENE)
	}
}