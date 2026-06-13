package com.darkrockstudios.apps.hammer.database.migration

import com.darkrockstudios.apps.hammer.EmbeddedPostgresConfig
import com.darkrockstudios.apps.hammer.StorageConfig
import com.darkrockstudios.apps.hammer.StorageMode
import com.darkrockstudios.apps.hammer.database.EmbeddedPostgresDatabase
import okio.FileSystem
import java.net.ServerSocket
import java.nio.file.Path

/**
 * Shared scaffolding for the migration test suite. Each test gets its own
 * temp directory pointed at via `user.home` (so `getRootDataDirectory()`
 * resolves there), and a free OS-assigned port for its embedded Postgres so
 * test classes can run in parallel without collisions.
 */
internal object MigrationTestSupport {

	/** Asks the OS for an unused ephemeral port, then releases it for Postgres to bind. */
	fun nextPort(): Int = ServerSocket(0).use { it.localPort }

	fun storageFor(port: Int = nextPort()): StorageConfig =
		StorageConfig(
			type = StorageMode.EMBEDDED,
			embedded = EmbeddedPostgresConfig(port = port, dataDirName = "pgdata-$port"),
		)

	/**
	 * Runs [block] with `System.getProperty("user.home")` redirected to
	 * [tempDir] so the migrator's `getRootDataDirectory()` resolves under it.
	 * Restores the original property on return.
	 */
	fun <T> withRedirectedHome(tempDir: Path, block: () -> T): T {
		val original = System.getProperty("user.home")
		System.setProperty("user.home", tempDir.toString())
		return try {
			block()
		} finally {
			System.setProperty("user.home", original)
		}
	}

	/** Returns the `~/hammer_data` directory under [tempDir], creating it. */
	fun hammerDataDir(tempDir: Path): java.io.File {
		val dir = tempDir.resolve("hammer_data").toFile()
		dir.mkdirs()
		return dir
	}

	/** Path to the legacy `server.db` under [tempDir]'s hammer_data. */
	fun legacyDbPath(tempDir: Path): java.io.File =
		hammerDataDir(tempDir).resolve("server.db")

	/**
	 * Opens the target Postgres for a test and runs [block] with it. Closes
	 * the database afterwards regardless of outcome.
	 */
	fun <T> withEmbeddedPostgres(storage: StorageConfig, block: (EmbeddedPostgresDatabase) -> T): T {
		val db = EmbeddedPostgresDatabase(storage.embedded, FileSystem.SYSTEM)
		db.initialize()
		return try {
			block(db)
		} finally {
			db.close()
		}
	}
}
