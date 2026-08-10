package com.darkrockstudios.apps.hammer.datamigrator.migrations

interface DataMigration {
	/** Stable unique id; forms the one-time completion marker key, so never rename it. */
	val id: String

	suspend fun migrate()
}