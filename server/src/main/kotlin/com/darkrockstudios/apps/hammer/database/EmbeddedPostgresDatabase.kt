package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.darkrockstudios.apps.hammer.EmbeddedPostgresConfig
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import okio.FileSystem
import java.sql.Connection
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

	private val dataDir = (getRootDataDirectory(fileSystem) / config.dataDirName).toFile()

	override val driver: JdbcDriver
		get() = _driver

	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		if (!dataDir.exists()) dataDir.mkdirs()

		embedded = EmbeddedPostgres.builder()
			.setDataDirectory(dataDir)
			.setCleanDataDirectory(false)
			.setPort(config.port)
			.setLocaleConfig("encoding", "UTF8")
			.setLocaleConfig("locale", "C")
			.start()

		val pgDataSource: DataSource = embedded.postgresDatabase
		hikari = buildHikariPool(pgDataSource)
		_driver = HikariJdbcDriver(hikari)

		PostgresSchemaInitializer.initialize(_driver)
		_serverDatabase = buildServerDatabase(_driver)

		// Best-effort clean shutdown on JVM exit (SIGINT / app stop).
		Runtime.getRuntime().addShutdownHook(Thread(::shutdown, "embedded-postgres-shutdown"))
	}

	override fun close() {
		shutdown()
	}

	private fun shutdown() {
		runCatching { if (::hikari.isInitialized) hikari.close() }
		runCatching { if (::embedded.isInitialized) embedded.close() }
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
