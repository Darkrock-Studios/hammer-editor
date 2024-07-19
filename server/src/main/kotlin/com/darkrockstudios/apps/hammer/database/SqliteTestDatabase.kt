package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.ServerDatabase

class SqliteTestDatabase : Database {
	private lateinit var driver: SqlDriver

	private lateinit var _serverDatabase: ServerDatabase
	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
		ServerDatabase.Schema.create(driver)
		_serverDatabase = ServerDatabase(driver)
	}

	override fun close() {
		driver.close()
	}
}