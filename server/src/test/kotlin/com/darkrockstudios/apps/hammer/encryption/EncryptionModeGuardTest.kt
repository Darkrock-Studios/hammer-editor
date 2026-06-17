package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class EncryptionModeGuardTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase

	@BeforeEach
	override fun setup() {
		super.setup()
		testDatabase = SqliteTestDatabase()
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
	fun `mode none with an encrypted entity refuses to boot`() {
		insertEntity(1, "AES/GCM/NoPadding")
		assertFailsWith<EncryptionModeMismatchException> {
			EncryptionModeGuard.verifyOnBoot(EncryptionMode.NONE, db())
		}
	}

	@Test
	fun `mode none with an encrypted review scene refuses to boot`() {
		insertScene(1, "AES/GCM/NoPadding")
		assertFailsWith<EncryptionModeMismatchException> {
			EncryptionModeGuard.verifyOnBoot(EncryptionMode.NONE, db())
		}
	}

	@Test
	fun `mode none with only plaintext rows boots`() {
		insertEntity(1, "none")
		insertEntity(2, null)
		insertScene(1, "none")
		EncryptionModeGuard.verifyOnBoot(EncryptionMode.NONE, db())
	}

	@Test
	fun `mode none on an empty database boots`() {
		EncryptionModeGuard.verifyOnBoot(EncryptionMode.NONE, db())
	}

	@Test
	fun `mode aes with encrypted rows boots`() {
		insertEntity(1, "AES/GCM/NoPadding")
		EncryptionModeGuard.verifyOnBoot(EncryptionMode.AES, db())
	}
}
