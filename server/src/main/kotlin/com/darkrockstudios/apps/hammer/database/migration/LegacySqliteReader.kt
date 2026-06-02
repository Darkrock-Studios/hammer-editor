package com.darkrockstudios.apps.hammer.database.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.database.legacy.LegacySqliteDatabase
import okio.FileSystem
import okio.Path
import java.util.Properties

/**
 * Read-only wrapper around the legacy `server.db` SqlDelight setup. Used by
 * the one-time SQLite-to-Postgres migrator. Not a [com.darkrockstudios.apps.hammer.database.Database]
 * implementation — does not participate in Koin DI and is owned solely by the
 * migrator's lifetime.
 */
class LegacySqliteReader(
	@Suppress("UNUSED_PARAMETER") fileSystem: FileSystem,
	private val databasePath: Path,
) : AutoCloseable {
	private lateinit var driver: JdbcSqliteDriver
	private lateinit var _database: LegacySqliteDatabase

	val database: LegacySqliteDatabase get() = _database

	/** True if the legacy `server.db` file exists on disk. */
	fun exists(): Boolean = databasePath.toFile().exists()

	fun open() {
		driver = JdbcSqliteDriver(
			url = "jdbc:sqlite:" + databasePath.toFile().absolutePath,
			properties = Properties().apply { put("foreign_keys", "false") },
		)
		_database = LegacySqliteDatabase(driver)
	}

	override fun close() {
		runCatching { if (::driver.isInitialized) driver.close() }
	}
}
