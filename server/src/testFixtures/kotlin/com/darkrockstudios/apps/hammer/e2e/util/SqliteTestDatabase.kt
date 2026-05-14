package com.darkrockstudios.apps.hammer.e2e.util

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import java.util.*

class SqliteTestDatabase(
	private val createSchema: Boolean = true,
	private val enforceForeignKeys: Boolean = true
) : Database {
	private lateinit var _driver: SqlDriver
	override val driver: SqlDriver
		get() = _driver

	private lateinit var _serverDatabase: ServerDatabase
	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		if (::_driver.isInitialized.not()) {
			_driver = JdbcSqliteDriver(
				url = JdbcSqliteDriver.IN_MEMORY,
				properties = Properties().apply { put("foreign_keys", if (enforceForeignKeys) "true" else "false") }
			)
			if (createSchema) {
				ServerDatabase.Schema.create(_driver)
				_driver.execute(null, "PRAGMA user_version = ${ServerDatabase.Schema.version}", 0)
			}
			_serverDatabase = ServerDatabase(_driver)
		}
	}

	fun execute(sql: String): QueryResult<Long> {
		return _driver.execute(null, sql, 0)
	}

	suspend fun executeAsync(sql: String): Long {
		return execute(sql).await()
	}

	override fun close() {
		_driver.close()
	}
}