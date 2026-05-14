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
			// Non-empty so the inherited Hash Entity / Save Entity / Load Entity tests
			// exercise the aliases field end-to-end through the server hash path.
			aliases = listOf("Alias One", "Alias Two"),
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
			// Different list so Hash Entity tests prove the field actually differentiates.
			aliases = listOf("Alias One"),
		)
	}
}