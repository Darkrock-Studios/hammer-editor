package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.datamigrator.migrations.DataMigration

/**
 * Runs ordered, idempotent data migrations against an already-initialized
 * [Database]. The legacy SQLite-flavored hooks (`PRAGMA user_version`,
 * `PRAGMA foreign_keys`) are gone — schema versioning lives in
 * `PostgresSchemaInitializer` now, and the one-shot SQLite-to-Postgres data
 * migration has its own dedicated entry point in [com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator].
 */
class DataMigrator(
	private val database: Database,
) {
	private val migrations = mutableListOf<DataMigration>()

	private fun addMigration(migration: DataMigration) {
		migrations.add(migration)
	}

	suspend fun runMigrations() {
		migrations.forEach { it.migrate() }
	}
}
