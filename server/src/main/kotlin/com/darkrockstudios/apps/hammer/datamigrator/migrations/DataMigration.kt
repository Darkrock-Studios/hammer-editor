package com.darkrockstudios.apps.hammer.datamigrator.migrations

interface DataMigration {
	suspend fun migrate()
}