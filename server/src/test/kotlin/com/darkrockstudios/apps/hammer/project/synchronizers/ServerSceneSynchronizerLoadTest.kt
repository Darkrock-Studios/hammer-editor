package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import io.mockk.mockk
import org.junit.Before
import kotlin.reflect.KClass

class ServerSceneSynchronizerLoadTest :
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
}