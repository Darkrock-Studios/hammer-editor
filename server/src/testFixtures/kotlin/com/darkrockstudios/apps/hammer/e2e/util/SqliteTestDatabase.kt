package com.darkrockstudios.apps.hammer.e2e.util

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.PostgresSchemaInitializer
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import com.darkrockstudios.apps.hammer.database.buildServerDatabase
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.Duration

/**
 * Test database backed by a single, process-wide Zonky embedded Postgres
 * shared across every test in the JVM. The schema is created once for the
 * whole JVM; each instance just truncates the user tables to start clean.
 */
class SqliteTestDatabase : Database {
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

		_driver = SharedTestPostgres.driver
		SharedTestPostgres.ensureSchema()
		SharedTestPostgres.truncateUserTables()
		_serverDatabase = buildServerDatabase(_driver)
		initialized = true
	}

	fun execute(sql: String): QueryResult<Long> = _driver.execute(null, sql, 0)
	suspend fun executeAsync(sql: String): Long = execute(sql).await()

	override fun close() {
		// No-op. The shared embedded postgres outlives any single
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
			// Default 10s startup wait flakes on loaded CI runners (initdb + boot under I/O contention).
			.setPGStartupWait(Duration.ofSeconds(30))
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

	private var schemaReady = false
	private val userTablesCsv: String by lazy {
		discoverUserTables().joinToString(", ").also {
			check(it.isNotEmpty()) { "No user tables discovered — was ensureSchema() called first?" }
		}
	}

	@Synchronized
	fun ensureSchema() {
		if (schemaReady) return
		PostgresSchemaInitializer.initialize(driver)
		schemaReady = true
	}

	/** Wipes every user table in one TRUNCATE; resets sequences too. */
	fun truncateUserTables() {
		driver.execute(null, "TRUNCATE TABLE $userTablesCsv RESTART IDENTITY CASCADE", 0)
	}

	/** Discover user tables once, after schema is initialized. */
	private fun discoverUserTables(): List<String> {
		val tables = mutableListOf<String>()
		driver.executeQuery(
			identifier = null,
			sql = """
				SELECT tablename FROM pg_tables
				WHERE schemaname = 'public' AND tablename <> '_schema_version'
			""".trimIndent(),
			mapper = { cursor ->
				while (cursor.next().value) cursor.getString(0)?.let(tables::add)
				QueryResult.Unit
			},
			parameters = 0,
			binders = null,
		)
		return tables
	}
}
