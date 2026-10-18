package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.EncryptionConfig
import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.datamigrator.migrations.GrandfatheredSecretException
import com.darkrockstudios.apps.hammer.datamigrator.migrations.GrandfatheredSecretMigration
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class GrandfatheredSecretMigrationTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var fileSystem: FakeFileSystem
	private val legacyPath: Path = "/hammer_data/server.secret".toPath()

	private val noKeyring = object : ServerSecretProvider {
		override fun loadKeyring(): String? = null
	}
	private val hasKeyring = object : ServerSecretProvider {
		override fun loadKeyring() = "{}"
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		fileSystem = FakeFileSystem()
		setupKoin()
	}

	private fun migration(provider: ServerSecretProvider, mode: EncryptionMode?) =
		GrandfatheredSecretMigration(
			provider = provider,
			fileSystem = fileSystem,
			legacySecretPath = legacyPath,
			serverConfig = ServerConfig(encryption = EncryptionConfig(mode)),
			database = db,
		)

	private fun writeLegacySecret() {
		fileSystem.createDirectories(legacyPath.parent!!)
		fileSystem.write(legacyPath) { writeUtf8("legacy-secret") }
	}

	private fun insertEncryptedRow() {
		val queries = db.serverDatabase
		queries.accountQueries.createAccount(
			email = "t@t.com", password_hash = "h", cipher_secret = "s", is_admin = false,
		)
		queries.projectQueries.createProject(userId = 1, name = "P", uuid = "5f5a2a1a-0c3f-4a7e-9a4c-2b1f6d7e8c90")
		queries.storyEntityQueries.insertNew(
			userId = 1, projectId = 1, id = 1, type = "scene",
			content = "ciphertext", hash = "h1", cipher = "aesgcm:v1",
		)
	}

	@Test
	fun `aes mode on a bare server secret is refused`() = runTest {
		writeLegacySecret()

		assertFailsWith<GrandfatheredSecretException> {
			migration(noKeyring, EncryptionMode.AES).migrate()
		}
	}

	@Test
	fun `encrypted rows on a bare server secret are refused even in plaintext mode`() = runTest {
		writeLegacySecret()
		insertEncryptedRow()

		assertFailsWith<GrandfatheredSecretException> {
			migration(noKeyring, EncryptionMode.NONE).migrate()
		}
	}

	@Test
	fun `a plaintext server with nothing encrypted keeps its token secret`() = runTest {
		writeLegacySecret()

		migration(noKeyring, null).migrate()
	}

	@Test
	fun `a keyring satisfies the guard whatever is left on disk`() = runTest {
		writeLegacySecret()
		insertEncryptedRow()

		migration(hasKeyring, EncryptionMode.AES).migrate()
	}

	@Test
	fun `no server secret means nothing to migrate`() = runTest {
		migration(noKeyring, EncryptionMode.AES).migrate()
	}
}
