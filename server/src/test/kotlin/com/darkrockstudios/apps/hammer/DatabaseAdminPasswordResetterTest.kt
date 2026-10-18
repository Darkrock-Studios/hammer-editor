package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AdminPasswordResetResult
import com.darkrockstudios.apps.hammer.database.EmbeddedPostgresDatabase
import okio.FileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** The real resetter against an embedded Postgres living under a throwaway user.home. */
class DatabaseAdminPasswordResetterTest {

	@TempDir
	lateinit var home: File

	private lateinit var previousHome: String

	// Port 0 lets Zonky pick a free one, so a running dev server on the default port cannot collide.
	private val config = ServerConfig(storage = StorageConfig(embedded = EmbeddedPostgresConfig(port = 0)))

	@BeforeEach
	fun setup() {
		previousHome = System.getProperty("user.home")
		System.setProperty("user.home", home.absolutePath)
	}

	@AfterEach
	fun tearDown() {
		System.setProperty("user.home", previousHome)
	}

	@Test
	fun `refuses a home with no embedded database instead of creating one`() {
		val e = assertFailsWith<DatabaseUnavailableException> {
			DatabaseAdminPasswordResetter().reset(config, email = null, newPassword = "qweasdZXC123")
		}

		assertContains(assertNotNull(e.message), "no embedded database")
		assertEquals(false, File(home, "hammer_data/pgdata").exists())
	}

	@Test
	fun `resets the admin stored in the configured embedded database`() {
		seedAdmin("admin@example.com")

		val result = DatabaseAdminPasswordResetter().reset(config, email = null, newPassword = "qweasdZXC123")

		assertEquals(AdminPasswordResetResult.Success("admin@example.com"), result)
	}

	private fun seedAdmin(email: String) {
		val db = EmbeddedPostgresDatabase(config.storage.embedded, FileSystem.SYSTEM)
		db.initialize()
		try {
			db.driver.execute(
				null,
				"INSERT INTO account(email, password_hash, cipher_secret, is_admin) VALUES ('$email', 'hash', 'secret', TRUE)",
				0,
			)
		} finally {
			db.close()
		}
	}
}
