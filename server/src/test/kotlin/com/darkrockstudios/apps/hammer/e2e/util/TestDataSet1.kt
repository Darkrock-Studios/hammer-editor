package com.darkrockstudios.apps.hammer.e2e.util

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.addDeletedProject
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAccount
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createProject
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createTestNote
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createTestScene
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.insertDeletedEntity
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.insertEntity
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.preDeletedProject1
import korlibs.io.util.UUID

object TestDataSet1 {
	val account1 = TestAccount(
		email = "test@test.com",
		password = "password123!@#",
		salt = "fake-salt",
	)
	val project1 = TestProject(
		name = "test-project",
		uuid = UUID.randomUUID(),
		userId = 1,
	)

	fun createFullDataset(database: SqliteTestDatabase) {
		createAccount(account1, database)
		createProject(project1, database)

		addDeletedProject(1, preDeletedProject1, database)

		user1Project1Entities.forEach { entity ->
			insertEntity(
				userId = 1,
				projectId = 1,
				entity = entity,
				testDatabase = database,
			)
		}

		user1Project1DeletedEntities.forEach { entityId ->
			insertDeletedEntity(
				id = entityId.toLong(),
				userId = 1,
				projectId = 1,
				testDatabase = database,
			)
		}
	}

	val user1Project1Entities: List<ApiProjectEntity> = buildList {
		(1..5).map { ii ->
			add(createTestScene(ii))
		}
		add(createTestNote(6))
		add(createTestNote(8))
	}

	val user1Project1DeletedEntities: List<Long> = buildList {
		add(7)
	}

	fun createEmptyDataset(database: SqliteTestDatabase) {
		createAccount(account1, database)
		createProject(project1, database)
	}
}