package com.darkrockstudios.apps.hammer.e2e.util

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.ServerDatabase
import com.darkrockstudios.apps.hammer.database.Database

class SqliteTestDatabase : Database {
	private lateinit var driver: SqlDriver

	private lateinit var _serverDatabase: ServerDatabase
	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		if (::driver.isInitialized.not()) {
			driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
			ServerDatabase.Schema.create(driver)
			_serverDatabase = ServerDatabase(driver)
		}
	}

	fun execute(sql: String): QueryResult<Long> {
		return driver.execute(null, sql, 0)
	}

	suspend fun executeAsync(sql: String): Long {
		return execute(sql).await()
	}

	override fun close() {
		driver.close()
	}
}