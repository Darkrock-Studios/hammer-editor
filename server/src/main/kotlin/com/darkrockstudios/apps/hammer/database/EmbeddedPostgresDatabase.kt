package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.darkrockstudios.apps.hammer.EmbeddedPostgresConfig
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import okio.FileSystem
import java.io.IOException
import java.sql.Connection
import java.time.Duration
import javax.sql.DataSource

/**
 * Boots an in-process Zonky PostgreSQL server alongside the app — the
 * "local install" / personal-server experience that replaces the old SQLite
 * single-file storage.
 *
 * Data lives at `<userHome>/hammer_data/<dataDirName>` and is preserved across
 * restarts (`setCleanDataDirectory(false)`). The bound port is pinned so the
 * operator can predict it.
 */
class EmbeddedPostgresDatabase(
	private val config: EmbeddedPostgresConfig,
	fileSystem: FileSystem,
) : Database {
	private lateinit var embedded: EmbeddedPostgres
	private lateinit var hikari: HikariDataSource
	private lateinit var _driver: JdbcDriver
	private lateinit var _serverDatabase: ServerDatabase
	private var shutdownHook: Thread? = null

	private val dataDir = (getRootDataDirectory(fileSystem) / config.dataDirName).toFile()

	override val driver: JdbcDriver
		get() = _driver

	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		check(shutdownHook == null) { "EmbeddedPostgresDatabase.initialize() called twice" }
		val startedEmpty = !dataDir.exists() || dataDir.list().isNullOrEmpty()
		if (!dataDir.exists()) dataDir.mkdirs()

		embedded = startEmbedded(resetOnFailure = startedEmpty)

		val pgDataSource: DataSource = embedded.postgresDatabase
		hikari = buildHikariPool(pgDataSource)
		_driver = HikariJdbcDriver(hikari)

		PostgresSchemaInitializer.initialize(_driver)
		_serverDatabase = buildServerDatabase(_driver)

		// Best-effort clean shutdown on JVM exit (SIGINT / app stop).
		shutdownHook = Thread(::shutdown, "embedded-postgres-shutdown").also {
			Runtime.getRuntime().addShutdownHook(it)
		}
	}

	override fun close() {
		shutdownHook?.let {
			// removeShutdownHook fails if the JVM is already shutting down — fine,
			// the hook will run momentarily anyway.
			runCatching { Runtime.getRuntime().removeShutdownHook(it) }
			shutdownHook = null
		}
		shutdown()
	}

	private fun shutdown() {
		runCatching { if (::hikari.isInitialized) hikari.close() }
		runCatching { if (::embedded.isInitialized) embedded.close() }
	}

	/**
	 * Boots the embedded server, retrying transient start failures. The Postgres
	 * process occasionally dies during boot on loaded runners (initdb / postmaster
	 * crash under I/O contention), which the startup wait can't help. A crashed
	 * boot can leave a partial data dir that blocks initdb on retry, so wipe it
	 * between attempts when [resetOnFailure] is set — only safe when we started
	 * from an empty dir and would not be destroying real data.
	 */
	private fun startEmbedded(resetOnFailure: Boolean): EmbeddedPostgres {
		var lastError: IOException? = null
		repeat(START_ATTEMPTS) { attempt ->
			try {
				return EmbeddedPostgres.builder()
					.setDataDirectory(dataDir)
					.setCleanDataDirectory(false)
					.setPort(config.port)
					.setLocaleConfig("encoding", "UTF8")
					.setLocaleConfig("locale", "C")
					.setPGStartupWait(Duration.ofSeconds(60))
					.start()
			} catch (e: IOException) {
				lastError = e
				if (resetOnFailure && attempt < START_ATTEMPTS - 1) {
					dataDir.deleteRecursively()
					dataDir.mkdirs()
				}
			}
		}
		throw IllegalStateException(
			"Embedded Postgres failed to start after $START_ATTEMPTS attempts on port ${config.port}",
			lastError,
		)
	}

	private fun buildHikariPool(source: DataSource): HikariDataSource {
		val hikariConfig = HikariConfig().apply {
			dataSource = source
			maximumPoolSize = 5          // embedded is single-server; tiny pool is enough
			minimumIdle = 1
			poolName = "embedded-postgres"
		}
		return HikariDataSource(hikariConfig)
	}

	private companion object {
		const val START_ATTEMPTS = 3
	}
}

/**
 * Minimal SqlDelight `JdbcDriver` that pulls connections from a Hikari pool.
 * Each call gets a fresh connection (no statement caching here — Hikari already
 * pools and Postgres caches plans server-side).
 */
internal class HikariJdbcDriver(private val pool: HikariDataSource) : JdbcDriver() {
	override fun getConnection(): Connection = pool.connection
	override fun closeConnection(connection: Connection) {
		connection.close()
	}

	override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
	override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
	override fun notifyListeners(vararg queryKeys: String) {}
}
