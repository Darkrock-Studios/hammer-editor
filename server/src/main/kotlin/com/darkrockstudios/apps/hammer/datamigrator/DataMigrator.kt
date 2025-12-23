package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.SqliteDatabase
import com.darkrockstudios.apps.hammer.datamigrator.migrations.DataMigration
import com.darkrockstudios.apps.hammer.datamigrator.migrations.DatabaseSchemaMigration
import com.darkrockstudios.apps.hammer.datamigrator.migrations.FilesystemToDatabaseMigration
import okio.FileSystem

class DataMigrator(
	fileSystem: FileSystem = FileSystem.SYSTEM,
	private val database: Database = SqliteDatabase(fileSystem, enforceForeignKeys = false)
) {
	private val migrations = mutableListOf<DataMigration>()

	init {
		database.initialize()
		addMigration(FilesystemToDatabaseMigration(fileSystem, database))
		addMigration(DatabaseSchemaMigration(database.driver))
	}

	private fun addMigration(migration: DataMigration) {
		migrations.add(migration)
	}

	suspend fun runMigrations() {
		migrations.forEach {
			it.migrate()
		}
		// Enable FK after migrations complete
		database.driver.execute(null, "PRAGMA foreign_keys = ON", 0)
		database.close()
	}
}