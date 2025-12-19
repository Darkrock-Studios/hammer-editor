package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass

class ServerEncyclopediaEntrySynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.EncyclopediaEntryEntity, ServerEncyclopediaSynchronizer>() {

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY
	override val entityClazz: KClass<ApiProjectEntity.EncyclopediaEntryEntity> =
		ApiProjectEntity.EncyclopediaEntryEntity::class
	override val pathStub: String = "encyclopedia_entry"

	@BeforeEach
	override fun setup() {
		super.setup()
	}

	override fun createSynchronizer(): ServerEncyclopediaSynchronizer {
		return ServerEncyclopediaSynchronizer(datasource)
	}

	override fun createNewEntity(): ApiProjectEntity.EncyclopediaEntryEntity {
		return ApiProjectEntity.EncyclopediaEntryEntity(
			id = 1,
			name = "Test Name",
			entryType = "Test Type",
			text = "Test Text",
			tags = setOf("Test Tag"),
			image = null,
		)
	}

	override fun createExistingEntity(): ApiProjectEntity.EncyclopediaEntryEntity {
		return ApiProjectEntity.EncyclopediaEntryEntity(
			id = 1,
			name = "Test Name Different",
			entryType = "Test Type",
			text = "Test Text",
			tags = setOf(),
			image = null,
		)
	}
}