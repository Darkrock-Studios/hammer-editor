package com.darkrockstudios.apps.hammer.database.migration

import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Re-running the migrator must be safe: it never re-migrates an already-
 * migrated database and never overwrites a backup that already exists.
 */
class MigrationIdempotencyTest {

	@Test
	fun `migrator is a no-op when no legacy server_db exists`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			// Don't seed anything; the migrator should immediately NoOp.
			MigrationTestSupport.hammerDataDir(tempDir) // creates parent only
			val storage = MigrationTestSupport.storageFor()

			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM).run()

			assertTrue(
				result is SqliteToPostgresMigrator.Result.NoOp,
				"no legacy server.db should be a NoOp; got $result",
			)
		}
	}

	@Test
	fun `migrator is a no-op when a backup already exists`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			MigrationFixtureBuilder.tiny(legacyDb.absolutePath)

			// Pretend an earlier run already migrated and renamed the file.
			val bak = MigrationTestSupport.hammerDataDir(tempDir)
				.resolve("server.db.migrated-20990101-000000.bak")
			bak.writeText("legacy-bytes")

			val storage = MigrationTestSupport.storageFor()
			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM).run()

			assertTrue(
				result is SqliteToPostgresMigrator.Result.NoOp,
				"backup present should short-circuit to NoOp; got $result",
			)
			// And we must not have re-renamed or removed the legacy file.
			assertTrue(legacyDb.exists(), "server.db must not be re-renamed")
			assertTrue(bak.exists(), "existing backup must not be touched")
		}
	}
}
