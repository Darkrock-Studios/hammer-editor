package com.darkrockstudios.apps.hammer.database.migration

import com.darkrockstudios.apps.hammer.EmbeddedPostgresConfig
import com.darkrockstudios.apps.hammer.StorageConfig
import com.darkrockstudios.apps.hammer.StorageMode
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Smoke test for [SqliteToPostgresMigrator]. Builds a tiny legacy SQLite
 * database from scratch (raw JDBC, no SqlDelight on the fixture side),
 * points an embedded-Postgres-backed [StorageConfig] at a fresh data dir, and
 * runs the migrator end-to-end. Asserts:
 *  - Migrator reports Success.
 *  - The legacy `server.db` is renamed to `server.db.migrated-*.bak`.
 *  - Row counts match (at least for the tables the migrator currently
 *    streams; see SqliteToPostgresMigrator for which are wired up).
 *
 * This test is the load-bearing safety net for the migrator's happy path.
 * Failure-mode and parity-edge-case tests live in sibling files (see plan §8).
 */
class SqliteToPostgresMigratorSmokeTest {

	private val portAllocator = AtomicInteger(54400)

	@Test
	fun `migrator copies a tiny legacy database and backs up the source`(@TempDir tempDir: Path) {
		// Place the legacy server.db under ~/hammer_data — but redirect that by
		// setting user.home to tempDir for the duration of the test, since
		// getRootDataDirectory() resolves via System.getProperty("user.home").
		val originalUserHome = System.getProperty("user.home")
		System.setProperty("user.home", tempDir.toString())

		try {
			val hammerData = tempDir.resolve("hammer_data").toFile().apply { mkdirs() }
			val legacyDb = hammerData.toPath().resolve("server.db").toFile()
			seedLegacyDatabase(legacyDb.absolutePath)

			val storage = StorageConfig(
				type = StorageMode.EMBEDDED,
				embedded = EmbeddedPostgresConfig(
					port = portAllocator.getAndIncrement(),
					dataDirName = "pgdata-smoketest",
				),
			)

			val migrator = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM, dryRun = false)
			val result = migrator.run()

			assertTrue(
				result is SqliteToPostgresMigrator.Result.Success,
				"migration should succeed, got $result",
			)
			val success = result
			assertEquals(2, success.rowCounts["account"], "two seeded accounts")
			// auth_token / project / etc. table coverage is partial in the current
			// migrator pass; smoke test asserts only the fully-wired tables.

			assertFalse(legacyDb.exists(), "server.db should be renamed away")
			val backup = hammerData.listFiles { f ->
				f.name.startsWith("server.db.migrated-") && f.name.endsWith(".bak")
			}
			assertTrue(backup != null && backup.isNotEmpty(), "backup .bak file should be present")
		} finally {
			System.setProperty("user.home", originalUserHome)
		}
	}

	/** Seed a tiny legacy SQLite database with the full legacy v5 schema. */
	private fun seedLegacyDatabase(dbPath: String) {
		val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
			url = "jdbc:sqlite:$dbPath",
			properties = java.util.Properties().apply { put("foreign_keys", "false") },
		)
		try {
			com.darkrockstudios.apps.hammer.database.legacy.LegacySqliteDatabase.Schema.create(driver)
			val db = com.darkrockstudios.apps.hammer.database.legacy.LegacySqliteDatabase(driver)
			db.accountQueries.createAccount(
				email = "alice@example.com",
				password_hash = "\$argon2id\$fake-hash-for-alice",
				cipher_secret = UUID.randomUUID().toString(),
				is_admin = false,
			)
			db.accountQueries.createAccount(
				email = "bob@example.com",
				password_hash = "\$argon2id\$fake-hash-for-bob",
				cipher_secret = UUID.randomUUID().toString(),
				is_admin = false,
			)
		} finally {
			driver.close()
		}
	}
}
