package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EncryptionModeGuardTest : BaseTest() {

	private lateinit var testDatabase: SharedPostgresTestDatabase

	@BeforeEach
	override fun setup() {
		super.setup()
		testDatabase = SharedPostgresTestDatabase()
		testDatabase.initialize()
	}

	private fun db() = testDatabase.serverDatabase

	private fun insertEntity(id: Long, cipher: String?) {
		db().storyEntityQueries.insertNew(
			userId = 1, projectId = 1, id = id, type = "scene",
			content = "x", hash = "h", cipher = cipher,
		)
	}

	private fun insertScene(id: Long, cipher: String) {
		db().reviewSceneQueries.createScene(
			reviewRequestId = id, sceneId = 1, draftId = 1,
			sceneName = "s", sceneOrder = 0, snapshotContent = "x", cipher = cipher,
		)
	}

	@Test
	fun `counts only encrypted rows across both tables`() {
		insertEntity(1, "aesgcm:v1")
		insertEntity(2, "none")
		insertEntity(3, null)
		insertEntity(4, "AES/GCM/NoPadding")
		insertScene(1, "aesgcm:v1")
		insertScene(2, "none")

		// Encrypted: entities 1 & 4, scene 1 -> 3. Plaintext (none/NULL) excluded.
		assertEquals(3, EncryptionModeGuard.encryptedRowCount(db()))
	}

	@Test
	fun `empty database has no encrypted rows`() {
		assertEquals(0, EncryptionModeGuard.encryptedRowCount(db()))
	}
}
