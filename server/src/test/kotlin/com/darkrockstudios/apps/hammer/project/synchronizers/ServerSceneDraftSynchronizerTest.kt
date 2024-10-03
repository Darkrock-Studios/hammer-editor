package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass

class ServerSceneDraftSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.SceneDraftEntity, ServerSceneDraftSynchronizer>() {

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.SCENE_DRAFT
	override val entityClazz: KClass<ApiProjectEntity.SceneDraftEntity> =
		ApiProjectEntity.SceneDraftEntity::class
	override val pathStub: String = "scene_draft"

	@BeforeEach
	override fun setup() {
		super.setup()
	}

	override fun createSynchronizer(): ServerSceneDraftSynchronizer {
		return ServerSceneDraftSynchronizer(datasource)
	}

	override fun createNewEntity(): ApiProjectEntity.SceneDraftEntity {
		return ApiProjectEntity.SceneDraftEntity(
			id = 1,
			sceneId = 1,
			content = "Test Content",
			created = Instant.fromEpochSeconds(123),
			name = "Test Name",
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.SceneDraftEntity {
		return ApiProjectEntity.SceneDraftEntity(
			id = 1,
			sceneId = 1,
			content = "Test Content Different",
			created = Instant.fromEpochSeconds(123),
			name = "Test Name Different",
		)
	}
}