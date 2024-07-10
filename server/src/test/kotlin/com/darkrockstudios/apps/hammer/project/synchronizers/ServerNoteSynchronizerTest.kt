package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import kotlinx.datetime.Instant
import org.junit.Before
import kotlin.reflect.KClass

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
}