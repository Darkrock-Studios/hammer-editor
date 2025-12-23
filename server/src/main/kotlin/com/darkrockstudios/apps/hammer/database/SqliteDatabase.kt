package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import okio.FileSystem
import java.util.*

class SqliteDatabase(
	fileSystem: FileSystem,
	private val enforceForeignKeys: Boolean = true
) : Database {
	private lateinit var _driver: JdbcSqliteDriver
	override val driver: JdbcSqliteDriver
		get() = _driver

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

		_driver = JdbcSqliteDriver(
			url = "jdbc:sqlite:" + dbFile.absolutePath,
			properties = Properties().apply {
				put("foreign_keys", if (enforceForeignKeys) "true" else "false")
			}
		)

		if (!dbFile.exists()) {
			ServerDatabase.Schema.create(_driver)
			_driver.execute(null, "PRAGMA user_version = ${ServerDatabase.Schema.version}", 0)
		}

		_serverDatabase = ServerDatabase(_driver)
	}

	override fun close() {
		_driver.close()
	}
}