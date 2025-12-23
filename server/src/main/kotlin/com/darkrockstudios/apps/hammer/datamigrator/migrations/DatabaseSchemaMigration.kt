package com.darkrockstudios.apps.hammer.datamigrator.migrations

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.darkrockstudios.apps.hammer.database.ServerDatabase

class DatabaseSchemaMigration(
	private val driver: SqlDriver
) : DataMigration {

	override suspend fun migrate() {
		val currentVersion = getSchemaVersion()
		val targetVersion = ServerDatabase.Schema.version

		if (currentVersion < targetVersion) {
			println("Running database schema migration from version $currentVersion to $targetVersion...")

			ServerDatabase.Schema.migrate(driver, currentVersion, targetVersion)

			setSchemaVersion(targetVersion)
			println("Database schema migration complete.")
		} else {
			println("Database schema is up to date (version $currentVersion).")
		}
	}

	private fun getSchemaVersion(): Long {
		return driver.executeQuery(
			identifier = null,
			sql = "PRAGMA user_version",
			mapper = { cursor -> QueryResult.Value(cursor.getLong(0)) },
			parameters = 0,
			binders = null
		).value ?: 0L
	}

	private fun setSchemaVersion(version: Long) {
		driver.execute(
			identifier = null,
			sql = "PRAGMA user_version = $version",
			parameters = 0,
			binders = null
		)
	}
}
