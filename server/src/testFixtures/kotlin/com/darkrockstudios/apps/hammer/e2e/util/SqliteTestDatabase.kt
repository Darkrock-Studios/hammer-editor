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
 * shared across every test in the JVM. The class name is preserved (it used
 * to wrap in-memory SQLite) so dependent tests don't have to be retouched.
 *
 * Each call to [initialize] resets the schema to a fresh v1 state by dropping
 * the `public` schema and re-running [PostgresSchemaInitializer]. That gives
 * per-test isolation without the cost of forking a new postgres process per
 * test method (which on Windows quickly exhausts handles and crashes the test
 * JVM with `EOFException` at the Gradle worker boundary).
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
		// Idempotent within a single instance. Tests get a fresh schema by
		// constructing a new SqliteTestDatabase, not by re-initializing the
		// same one — the production `Database` is initialized again by
		// `configureDependencyInjection`, and that second call must NOT wipe
		// the fixture data the test just loaded.
		if (initialized) return

		val sharedDriver = SharedTestPostgres.driver
		// Reset schema state for this fresh instance. Dropping and recreating
		// `public` wipes every table, extension, sequence, type — leaving a
		// clean slate before `PostgresSchemaInitializer` rebuilds.
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
	}
}
