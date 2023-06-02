package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.ServerDatabase
import com.darkrockstudios.apps.hammer.getRootDataDirectory
import okio.FileSystem

class SqliteDatabase(fileSystem: FileSystem) : Database {
	private lateinit var driver: SqlDriver

	private lateinit var _serverDatabase: ServerDatabase
	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	private val DATABASE_FILE = "server.db"
	private val databasePath = getRootDataDirectory(fileSystem) / DATABASE_FILE

	override fun initialize() {
		val dbFile = databasePath.toFile()
		if (!dbFile.parentFile.exists()) {
			dbFile.parentFile.mkdirs()
		}
		driver = JdbcSqliteDriver(url = "jdbc:sqlite:" + dbFile.absolutePath)

		if (!dbFile.exists()) {
			ServerDatabase.Schema.create(driver)
		}

		_serverDatabase = ServerDatabase(driver)
	}

	override fun close() {
		driver.close()
	}
}