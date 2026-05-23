package com.darkrockstudios.apps.hammer.e2e.util

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.PostgresSchemaInitializer
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import com.darkrockstudios.apps.hammer.database.buildServerDatabase
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

/**
 * Test database backed by a single, process-wide Zonky embedded Postgres
 * shared across every test in the JVM. Each instance drops and recreates
 * `public` to start from a clean v1 schema. A new instance per test gives
 * isolation without paying for a fresh postgres process each time.
 */
class SqliteTestDatabase(
	private val createSchema: Boolean = true,
	@Suppress("UNUSED_PARAMETER") enforceForeignKeys: Boolean = true,
) : Database {
	private lateinit var _driver: SqlDriver
	private lateinit var _serverDatabase: ServerDatabase
	private var initialized = false

	override val driver: SqlDriver get() = _driver
	override val serverDatabase: ServerDatabase get() = _serverDatabase

	override fun initialize() {
		// Idempotent: configureDependencyInjection re-initializes the Database
		// bean after the test has already loaded fixture data; a second wipe
		// would destroy that data.
		if (initialized) return

		val sharedDriver = SharedTestPostgres.driver
		// Wipe tables, sequences, types, and extensions back to v1 baseline.
		sharedDriver.execute(null, "DROP SCHEMA public CASCADE", 0)
		sharedDriver.execute(null, "CREATE SCHEMA public", 0)

		_driver = sharedDriver
		if (createSchema) {
			PostgresSchemaInitializer.initialize(_driver)
		}
		_serverDatabase = buildServerDatabase(_driver)
		initialized = true
	}

	fun execute(sql: String): QueryResult<Long> = _driver.execute(null, sql, 0)
	suspend fun executeAsync(sql: String): Long = execute(sql).await()

	override fun close() {
		// Intentionally no-op. The shared embedded postgres outlives any single
		// SqliteTestDatabase; it is shut down by SharedTestPostgres's JVM hook.
	}
}

/**
 * Lazily-initialized embedded Postgres shared across every test in the JVM.
 * Started on first access; stopped via a JVM shutdown hook (best-effort).
 */
private object SharedTestPostgres {
	private val embedded: EmbeddedPostgres by lazy {
		val pg = EmbeddedPostgres.builder()
			.setPort(0)
			.setLocaleConfig("encoding", "UTF8")
			.setLocaleConfig("locale", "C")
			.start()
		Runtime.getRuntime().addShutdownHook(Thread {
			runCatching { pg.close() }
		})
		pg
	}

	val driver: SqlDriver by lazy {
		val ds = embedded.postgresDatabase
		object : app.cash.sqldelight.driver.jdbc.JdbcDriver() {
			override fun getConnection(): java.sql.Connection {
				val conn = ds.connection
				// Fixtures often insert rows out of FK order (e.g. a deleted_project
				// for an account that doesn't exist); disable FK enforcement so
				// those setups stand.
				conn.createStatement().use { it.execute("SET session_replication_role = replica") }
				return conn
			}
			override fun closeConnection(connection: java.sql.Connection) = connection.close()
			override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
			override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
			override fun notifyListeners(vararg queryKeys: String) {}
		}
	}
}
