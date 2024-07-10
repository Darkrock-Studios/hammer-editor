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
import org.junit.Before
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerSceneSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.SceneEntity, ServerSceneSynchronizer>() {

	private lateinit var log: io.ktor.util.logging.Logger

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.SCENE
	override val entityClazz: KClass<ApiProjectEntity.SceneEntity> =
		ApiProjectEntity.SceneEntity::class
	override val pathStub: String = "scene"

	@Before
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
			return@answers SResult.success(entities[entityIdSlot.captured - 1])
		}

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

	@OptIn(InternalSerializationApi::class)
	@Test
	fun `Get Scene Update Sequence - All the same`() = runTest {
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

	private fun entityDefs() = listOf(
		EntityDefinition(1, ApiProjectEntity.Type.SCENE),
		EntityDefinition(2, ApiProjectEntity.Type.SCENE),
		EntityDefinition(3, ApiProjectEntity.Type.SCENE),
	)
}