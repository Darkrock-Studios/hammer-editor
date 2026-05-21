package com.darkrockstudios.apps.hammer.database.migration

import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Negative tests for [SqliteToPostgresMigrator]: every condition that should
 * abort the migration must leave the legacy `server.db` file untouched.
 */
class MigrationFailureModesTest {

	@Test
	fun `aborts when target Postgres is already populated`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			MigrationFixtureBuilder.tiny(legacyDb.absolutePath)

			val storage = MigrationTestSupport.storageFor()

			// Pre-seed Postgres with one account before the migrator runs.
			MigrationTestSupport.withEmbeddedPostgres(storage) { db ->
				db.serverDatabase.accountQueries.createAccount(
					email = "preexisting@example.com",
					password_hash = "h",
					cipher_secret = "s",
					is_admin = false,
				)
			}

			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM).run()

			val aborted = result as? SqliteToPostgresMigrator.Result.Aborted
			assertNotNull(aborted, "should abort with non-empty Postgres; got $result")
			assertTrue(
				aborted.reason.contains("not empty", ignoreCase = true),
				"abort reason should explain Postgres is not empty: ${aborted.reason}",
			)
			assertTrue(legacyDb.exists(), "server.db must NOT be renamed when migration aborts")
		}
	}

	@Test
	fun `aborts when legacy data contains a malformed UUID`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			seedWithBadUuid(legacyDb.absolutePath)

			val storage = MigrationTestSupport.storageFor()
			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM).run()

			val aborted = result as? SqliteToPostgresMigrator.Result.Aborted
			assertNotNull(aborted, "should abort on malformed UUID; got $result")
			assertTrue(
				aborted.reason.contains("UUID", ignoreCase = true) ||
					aborted.reason.contains("malformed", ignoreCase = true),
				"abort reason should mention the bad UUID: ${aborted.reason}",
			)
			assertTrue(legacyDb.exists(), "server.db must NOT be renamed when migration aborts")

			// And Postgres should be empty — the failed transaction rolled back.
			MigrationTestSupport.withEmbeddedPostgres(storage) { db ->
				assertEquals(0L, db.serverDatabase.accountQueries.count().executeAsOne())
			}
		}
	}

	@Test
	fun `dry-run never commits and never renames the source file`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			MigrationFixtureBuilder.tiny(legacyDb.absolutePath)

			val storage = MigrationTestSupport.storageFor()
			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM, dryRun = true).run()

			// Dry-run reports Success (parity passed) but does not rename the file.
			assertTrue(
				result is SqliteToPostgresMigrator.Result.Success,
				"dry-run should report Success on a clean fixture; got $result",
			)
			assertTrue(legacyDb.exists(), "dry-run must NOT rename server.db")

			// Postgres rolled back: no rows landed.
			MigrationTestSupport.withEmbeddedPostgres(storage) { db ->
				assertEquals(0L, db.serverDatabase.accountQueries.count().executeAsOne())
			}
		}
	}

	/** Schema-only fixture with one project referencing a not-actually-a-UUID. */
	private fun seedWithBadUuid(dbPath: String) = MigrationFixtureBuilder.withSchema(dbPath) { db ->
		db.accountQueries.createAccount(
			email = "a@example.com",
			password_hash = "h",
			cipher_secret = "s",
			is_admin = false,
		)
		db.projectQueries.insertProject(
			userId = 1,
			name = "Bad UUID Project",
			uuid = "not-a-uuid",
			lastSync = "2024-01-01 00:00:00",
			lastId = 0,
		)
	}
}
