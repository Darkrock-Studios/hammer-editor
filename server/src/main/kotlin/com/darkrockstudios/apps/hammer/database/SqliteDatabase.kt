package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import okio.FileSystem
import java.util.Properties

class SqliteDatabase(fileSystem: FileSystem) : Database {
	private lateinit var driver: JdbcSqliteDriver

	private lateinit var _serverDatabase: ServerDatabase
	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	private val DATABASE_FILE = "server.db"
	private val databasePath = getRootDataDirectory(fileSystem) / DATABASE_FILE

	override fun initialize() {
		val dbFile = databasePath.toFile()
		if (dbFile.parentFile?.exists() == false) {
			dbFile.parentFile.mkdirs()
		}

		driver = JdbcSqliteDriver(
			url = "jdbc:sqlite:" + dbFile.absolutePath,
			properties = Properties().apply { put("foreign_keys", "true") }
		)

		if (!dbFile.exists()) {
			ServerDatabase.Schema.create(driver)
			setSchemaVersion()
		} else {
			val currentVersion = getSchemaVersion()
			ServerDatabase.Schema.migrate(driver, currentVersion, ServerDatabase.Schema.version)
			setSchemaVersion()
		}

		_serverDatabase = ServerDatabase(driver)
	}

	private fun getSchemaVersion(): Long {
		val currentVersion = driver.executeQuery(
			identifier = null,
			sql = "PRAGMA user_version",
			mapper = { cursor -> QueryResult.Value(cursor.getLong(0)) },
			parameters = 0,
			binders = null
		).value ?: 1L
		return currentVersion
	}

	private fun setSchemaVersion() {
		driver.execute(
			identifier = null,
			sql = "PRAGMA user_version = ${ServerDatabase.Schema.version}",
			parameters = 0,
			binders = null
		)
	}

	override fun close() {
		driver.close()
	}
}