package com.darkrockstudios.apps.hammer.database.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * Scaffolding for the schema-upgrade test suite. One embedded Postgres server is
 * booted for the whole JVM; each scenario gets its own freshly-created database
 * inside it (cheap — a CREATE DATABASE, not another postmaster). That isolation
 * lets one test apply the v1 baseline and migrate it forward while another runs a
 * pristine fresh install, without either seeing the other's tables.
 */
internal object PostgresUpgradeTestSupport {

	private val counter = AtomicInteger(0)

	private val embedded: EmbeddedPostgres by lazy {
		val pg = EmbeddedPostgres.builder()
			.setPort(0)
			.setLocaleConfig("encoding", "UTF8")
			.setLocaleConfig("locale", "C")
			.setPGStartupWait(Duration.ofSeconds(30))
			.start()
		Runtime.getRuntime().addShutdownHook(Thread { runCatching { pg.close() } })
		pg
	}

	/** Boots an empty, isolated database and returns a handle to it. */
	fun freshDatabase(): IsolatedDatabase {
		val name = "mig_test_${counter.incrementAndGet()}"
		embedded.postgresDatabase.connection.use { conn ->
			conn.createStatement().use { it.execute("CREATE DATABASE $name") }
		}
		return IsolatedDatabase(embedded.getDatabase("postgres", name))
	}

	/** Loads a checked-in schema baseline snapshot from test resources. */
	fun loadBaseline(version: Int): String {
		val path = "/postgres-schema/v${version}_baseline.sql"
		return requireNotNull(javaClass.getResource(path)) { "missing baseline snapshot: $path" }
			.readText()
	}

	/** Runs every statement in [sql] against [driver], in order. */
	fun applyScript(driver: SqlDriver, sql: String) {
		for (statement in splitStatements(sql)) {
			driver.execute(null, statement, 0)
		}
	}

	/**
	 * Splits a SQL script into statements on `;`. Line comments are stripped first
	 * so a `;` inside a comment doesn't get mistaken for a statement terminator.
	 * Safe for these scripts because none of the string literals contain `--`.
	 */
	private fun splitStatements(sql: String): List<String> {
		val withoutComments = sql.lineSequence().joinToString("\n") { line ->
			val comment = line.indexOf("--")
			if (comment >= 0) line.substring(0, comment) else line
		}
		return withoutComments.split(";").map { it.trim() }.filter { it.isNotEmpty() }
	}
}

/**
 * One isolated database inside the shared embedded server. Exposes a SqlDelight
 * [SqlDriver] for running DDL / migrations / queries and raw [Connection]s for
 * catalog introspection.
 */
internal class IsolatedDatabase(private val dataSource: DataSource) {

	val driver: SqlDriver = object : JdbcDriver() {
		override fun getConnection(): Connection = dataSource.connection
		override fun closeConnection(connection: Connection) = connection.close()
		override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
		override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
		override fun notifyListeners(vararg queryKeys: String) {}
	}

	fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)
}
