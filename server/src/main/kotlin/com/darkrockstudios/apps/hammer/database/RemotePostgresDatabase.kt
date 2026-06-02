package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.darkrockstudios.apps.hammer.RemotePostgresConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Connects to an externally-managed PostgreSQL server — the "production" mode
 * where the operator runs their own DB (or a managed service).
 */
class RemotePostgresDatabase(
	private val config: RemotePostgresConfig,
) : Database {
	private lateinit var hikari: HikariDataSource
	private lateinit var _driver: JdbcDriver
	private lateinit var _serverDatabase: ServerDatabase

	override val driver: JdbcDriver
		get() = _driver

	override val serverDatabase: ServerDatabase
		get() = _serverDatabase

	override fun initialize() {
		hikari = buildHikariPool()
		_driver = HikariJdbcDriver(hikari)
		PostgresSchemaInitializer.initialize(_driver)
		_serverDatabase = buildServerDatabase(_driver)
	}

	override fun close() {
		runCatching { if (::hikari.isInitialized) hikari.close() }
	}

	private fun buildHikariPool(): HikariDataSource {
		val sslSuffix = if (config.useSsl) "&ssl=true" else ""
		val url = buildString {
			append("jdbc:postgresql://")
			append(config.host)
			append(':')
			append(config.port)
			append('/')
			append(config.database)
			append("?currentSchema=")
			append(config.schema)
			append(sslSuffix)
		}
		val hikariConfig = HikariConfig().apply {
			jdbcUrl = url
			username = config.user
			password = config.password
			maximumPoolSize = config.poolSize
			minimumIdle = 1
			poolName = "remote-postgres"
		}
		return HikariDataSource(hikariConfig)
	}
}
