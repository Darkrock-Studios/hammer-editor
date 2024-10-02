package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.datamigrator.migrations.DataMigration
import com.darkrockstudios.apps.hammer.datamigrator.migrations.FilesystemToDatabaseMigration

class DataMigrator {
	private val migrations = mutableListOf<DataMigration>()

	init {
		addMigration(FilesystemToDatabaseMigration())
	}

	private fun addMigration(migration: DataMigration) {
		migrations.add(migration)
	}

	suspend fun runMigrations() {
		migrations.forEach {
			it.migrate()
		}
	}
}