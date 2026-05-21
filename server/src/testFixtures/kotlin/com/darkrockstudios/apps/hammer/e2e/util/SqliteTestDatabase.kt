package com.darkrockstudios.apps.hammer.e2e.util

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.PostgresSchemaInitializer
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import com.darkrockstudios.apps.hammer.database.buildServerDatabase
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test database backed by Zonky embedded Postgres. The class name is kept as
 * `SqliteTestDatabase` for now to keep churn in existing tests minimal; it
 * really wraps an embedded Postgres instance and runs the production
 * [PostgresSchemaInitializer] so the schema mirrors production exactly.
 *
 * Each instance starts its own embedded server on an ephemeral port — tests
 * are fully isolated. Call [close] (or use try-finally) to stop it.
 */
class SqliteTestDatabase(
	private val createSchema: Boolean = true,
	@Suppress("UNUSED_PARAMETER") enforceForeignKeys: Boolean = true,
) : Database {
	private lateinit var embedded: EmbeddedPostgres
	private lateinit var _driver: SqlDriver
	private lateinit var _serverDatabase: ServerDatabase

	override val driver: SqlDriver get() = _driver
	override val serverDatabase: ServerDatabase get() = _serverDatabase

	override fun initialize() {
		if (::_driver.isInitialized) return

		embedded = EmbeddedPostgres.builder()
			.setPort(0) // random free port; isolates parallel test classes
			.setLocaleConfig("encoding", "UTF8")
			.setLocaleConfig("locale", "C")
			.start()

		val ds = embedded.postgresDatabase
		_driver = object : app.cash.sqldelight.driver.jdbc.JdbcDriver() {
			override fun getConnection(): java.sql.Connection {
				val conn = ds.connection
				// Tests historically ran against SQLite with `foreign_keys=false`,
				// so many fixtures insert rows out of FK order (e.g. a
				// `deleted_project` for an account that hasn't been created).
				// Disable FK enforcement on every connection so test setup
				// behaves like it did on SQLite.
				conn.createStatement().use { it.execute("SET session_replication_role = replica") }
				return conn
			}
			override fun closeConnection(connection: java.sql.Connection) = connection.close()
			override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
			override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
			override fun notifyListeners(vararg queryKeys: String) {}
		}

		if (createSchema) {
			PostgresSchemaInitializer.initialize(_driver)
		}
		_serverDatabase = buildServerDatabase(_driver)
	}

	fun execute(sql: String): QueryResult<Long> {
		return _driver.execute(null, sql, 0)
	}

	suspend fun executeAsync(sql: String): Long {
		return execute(sql).await()
	}

	override fun close() {
		runCatching { if (::_driver.isInitialized) _driver.close() }
		runCatching { if (::embedded.isInitialized) embedded.close() }
	}

	companion object {
		// Each test class gets its own EmbeddedPostgres instance; this counter is
		// just so we know how many are alive for debugging if needed.
		val liveInstanceCount = AtomicInteger(0)
	}
}
