package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.ServerConfigKey
import com.darkrockstudios.apps.hammer.datamigrator.migrations.DataMigration

/**
 * Runs ordered, one-time data migrations against an already-initialized database
 * at startup. Each migration runs exactly once per server: a completion marker in
 * `server_config` guards re-runs, so a migration is never re-applied on later
 * boots. A migration failure propagates and aborts startup.
 *
 * Schema versioning lives in `PostgresSchemaInitializer`, and the one-shot
 * SQLite-to-Postgres data migration has its own dedicated entry point in
 * [com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator].
 */
class DataMigrator(
	private val configRepository: ConfigRepository,
) {
	private val migrations = mutableListOf<DataMigration>()

	fun addMigration(migration: DataMigration) {
		migrations.add(migration)
	}

	suspend fun runMigrations() {
		migrations.forEach { migration ->
			val marker = markerKey(migration.id)
			if (configRepository.get(marker).not()) {
				migration.migrate()
				configRepository.set(marker, true)
			}
		}
	}

	private fun markerKey(id: String): ServerConfigKey<Boolean> =
		ServerConfigKey.boolean("datamigration_${id}_complete", false)
}
