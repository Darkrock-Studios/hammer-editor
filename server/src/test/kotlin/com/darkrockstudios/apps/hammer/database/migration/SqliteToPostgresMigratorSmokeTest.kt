package com.darkrockstudios.apps.hammer.database.migration

import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test for [SqliteToPostgresMigrator]: seeds a tiny legacy SQLite
 * database, runs the migrator end-to-end against a fresh embedded Postgres,
 * asserts Success + that the source file was renamed to a `.bak`. Detailed
 * per-table parity lives in [MigrationFullTableParityTest].
 */
class SqliteToPostgresMigratorSmokeTest {

	@Test
	fun `migrator copies a tiny legacy database and backs up the source`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			val expected = MigrationFixtureBuilder.tiny(legacyDb.absolutePath).expectedRowCounts

			val storage = MigrationTestSupport.storageFor()
			val result = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM, dryRun = false).run()

			val success = result as? SqliteToPostgresMigrator.Result.Success
			assertNotNull(success, "migration should succeed; got $result")
			assertEquals(expected["account"], success.rowCounts["account"])

			assertFalse(legacyDb.exists(), "server.db should be renamed away")
			val baks = MigrationTestSupport.hammerDataDir(tempDir).listFiles { f ->
				f.name.startsWith("server.db.migrated-") && f.name.endsWith(".bak")
			}
			assertTrue(baks != null && baks.isNotEmpty(), "backup .bak file should be present")
		}
	}
}
