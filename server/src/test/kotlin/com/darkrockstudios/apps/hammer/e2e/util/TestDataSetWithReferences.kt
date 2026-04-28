package com.darkrockstudios.apps.hammer.e2e.util

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAccount
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createProject
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createTestEncyclopediaEntry
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createTestSceneWithReferences
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.insertEntity
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor

/**
 * E2E dataset that exercises the new reference / alias fields end-to-end through the
 * sync pipeline (HTTP serialization, server hash computation, database persistence).
 *
 * Reuses [TestDataSet1.account1] and [TestDataSet1.project1] so the same auth +
 * routing setup the rest of the E2E suite uses works here too.
 */
object TestDataSetWithReferences {

	const val SCENE_WITH_REFS_ID = 1
	const val ENTRY_WITH_ALIASES_ID = 2

	val sceneWithRefs: ApiProjectEntity.SceneEntity = createTestSceneWithReferences(
		id = SCENE_WITH_REFS_ID,
		confirmedReferences = setOf(2, 5, 11),
		dismissedReferences = setOf(99),
	)

	val entryWithAliases: ApiProjectEntity.EncyclopediaEntryEntity = createTestEncyclopediaEntry(
		id = ENTRY_WITH_ALIASES_ID,
		name = "Robert",
		aliases = listOf("Bob", "Bobby", "Rob"),
	)

	val entities: List<ApiProjectEntity> = listOf(sceneWithRefs, entryWithAliases)

	fun createFullDataset(database: SqliteTestDatabase, contentEncryptor: ContentEncryptor) {
		createAccount(TestDataSet1.account1, database)
		createProject(TestDataSet1.project1, database)

		entities.forEach { entity ->
			insertEntity(
				userId = 1,
				projectId = 1,
				entity = entity,
				testDatabase = database,
				contentEncryptor = contentEncryptor,
			)
		}
	}
}
