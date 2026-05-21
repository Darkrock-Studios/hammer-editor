package com.darkrockstudios.apps.hammer.database.migration

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.darkrockstudios.apps.hammer.StorageConfig
import com.darkrockstudios.apps.hammer.StorageMode
import com.darkrockstudios.apps.hammer.database.EmbeddedPostgresDatabase
import com.darkrockstudios.apps.hammer.database.RemotePostgresDatabase
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import com.darkrockstudios.apps.hammer.database.legacy.LegacySqliteDatabase
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.darkrockstudios.apps.hammer.utilities.parseLegacyTimestamp
import okio.FileSystem
import okio.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * One-shot data migration: copies `~/hammer_data/server.db` into the
 * configured PostgreSQL backend, verifies row counts, then renames the
 * source file to `server.db.migrated-<timestamp>.bak`. Conservative
 * throughout — the SQLite file is never touched until the Postgres
 * transaction commits.
 */
class SqliteToPostgresMigrator(
	private val storage: StorageConfig,
	private val fileSystem: FileSystem,
	private val dryRun: Boolean = false,
) {

	sealed class Result {
		data object NoOp : Result()
		data class Success(val rowCounts: Map<String, Int>, val backupPath: Path) : Result()
		data class Aborted(val reason: String) : Result()
	}

	private val backupDir by lazy { getRootDataDirectory(fileSystem) }
	private val legacyPath: Path by lazy { backupDir / "server.db" }

	fun run(): Result {
		if (!legacyPath.toFile().exists()) return Result.NoOp
		if (backupExists()) return Result.NoOp

		val legacyReader = LegacySqliteReader(fileSystem, legacyPath).apply { open() }
		val database = openTargetDatabase(storage)
		database.initialize()

		try {
			if (countAccountRows(database.serverDatabase) > 0) {
				return Result.Aborted(
					"Postgres not empty — refusing to migrate. Drop the schema or " +
						"restore from server.db.migrated-*.bak and try again."
				)
			}

			val pgDriver = database.driver as JdbcDriver
			val connection = pgDriver.getConnection()
			connection.autoCommit = false

			try {
				connection.createStatement().execute("SET session_replication_role = replica")
				val rowCounts = copyAllTables(legacyReader.database, connection)
				connection.createStatement().execute("SET session_replication_role = DEFAULT")
				resetSequences(connection)

				// Parity check shares the same connection so it sees in-transaction
				// state — a fresh Hikari connection wouldn't under READ COMMITTED.
				val parity = MigrationParityChecker.checkAccountCount(
					legacyReader.database, connection
				)
				if (parity != null) {
					connection.rollback()
					return Result.Aborted("Parity check failed: $parity")
				}

				if (dryRun) {
					connection.rollback()
					return Result.Success(rowCounts, legacyPath)
				}

				connection.commit()
				return Result.Success(rowCounts, renameLegacyToBackup())
			} catch (t: Throwable) {
				runCatching { connection.rollback() }
				return Result.Aborted("Migration failed: ${t.message}")
			} finally {
				pgDriver.closeConnection(connection)
			}
		} finally {
			legacyReader.close()
			database.close()
		}
	}

	private fun backupExists(): Boolean {
		val parent = backupDir.toFile()
		if (!parent.exists()) return false
		return parent.listFiles { f -> f.name.startsWith("server.db.migrated-") && f.name.endsWith(".bak") }
			?.any() == true
	}

	private fun openTargetDatabase(cfg: StorageConfig) = when (cfg.type) {
		StorageMode.EMBEDDED -> EmbeddedPostgresDatabase(cfg.embedded, fileSystem)
		StorageMode.REMOTE -> RemotePostgresDatabase(
			cfg.remote ?: error("storage.type=remote requires storage.remote config block")
		)
	}

	private fun countAccountRows(db: ServerDatabase): Long =
		db.accountQueries.count().executeAsOne()

	private fun copyAllTables(legacy: LegacySqliteDatabase, conn: Connection): Map<String, Int> =
		linkedMapOf(
			"account" to copyAccount(legacy, conn),
			"auth_token" to copyAuthToken(legacy, conn),
			"password_reset_token" to copyPasswordResetToken(legacy, conn),
			"project" to copyProject(legacy, conn),
			"project_data" to copyProjectData(legacy, conn),
			"project_access" to copyProjectAccess(legacy, conn),
			"story_entity" to copyStoryEntity(legacy, conn),
			"deleted_project" to copyDeletedProject(legacy, conn),
			"deleted_entity" to copyDeletedEntity(legacy, conn),
			"server_config" to copyServerConfig(legacy, conn),
			"white_list" to copyWhiteList(legacy, conn),
			"writing_activity" to copyWritingActivity(legacy, conn),
		)

	/**
	 * Bulk-insert [rows] into Postgres via [sql], binding each row through
	 * [bind]. Flushes the JDBC batch every [BATCH_SIZE] rows so peak per-table
	 * memory in the driver buffer stays bounded — the legacy `.executeAsList()`
	 * still materializes the source rows in memory, but that's bounded by what
	 * fits in the single migration transaction anyway.
	 */
	private fun <T> copyTable(
		conn: Connection,
		sql: String,
		rows: List<T>,
		bind: PreparedStatement.(T) -> Unit,
	): Int {
		if (rows.isEmpty()) return 0
		conn.prepareStatement(sql).use { ps ->
			var batched = 0
			for (r in rows) {
				ps.bind(r)
				ps.addBatch()
				if (++batched % BATCH_SIZE == 0) ps.executeBatch()
			}
			if (batched % BATCH_SIZE != 0) ps.executeBatch()
		}
		return rows.size
	}

	private fun copyAccount(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO account
			(id, email, pen_name, password_hash, cipher_secret, created, is_admin,
			 last_sync, bio, email_verified, community_member)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		""".trimIndent(),
		legacy.accountQueries.getAllAccount().executeAsList(),
	) { r ->
		setLong(1, r.id)
		setString(2, r.email)
		setString(3, r.pen_name)
		setString(4, r.password_hash)
		setString(5, r.cipher_secret)
		setTimestamp(6, parseLegacyTimestampToSql(r.created))
		setBoolean(7, r.is_admin)
		setTimestamp(8, parseLegacyTimestampToSql(r.last_sync))
		setString(9, r.bio)
		setBoolean(10, r.email_verified)
		setBoolean(11, r.community_member)
	}

	private fun copyAuthToken(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		// Legacy PK is `token`; new PK is `(user_id, install_id)`. ON CONFLICT
		// handles the rare legacy row that had multiple tokens per install.
		"""
		INSERT INTO auth_token (user_id, install_id, token, refresh, created, expires)
		VALUES (?, ?, ?, ?, ?, ?)
		ON CONFLICT (user_id, install_id) DO UPDATE SET
			token = EXCLUDED.token,
			refresh = EXCLUDED.refresh,
			created = EXCLUDED.created,
			expires = EXCLUDED.expires
		""".trimIndent(),
		legacy.authTokenQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setString(2, r.install_id)
		setString(3, r.token)
		setString(4, r.refresh)
		setTimestamp(5, parseLegacyTimestampToSql(r.created))
		setTimestamp(6, parseLegacyTimestampToSql(r.expires))
	}

	private fun copyPasswordResetToken(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO password_reset_token (id, user_id, token, created, expires, used)
		VALUES (?, ?, ?, ?, ?, ?)
		""".trimIndent(),
		legacy.passwordResetTokenQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.id)
		setLong(2, r.user_id)
		setString(3, r.token)
		setTimestamp(4, parseLegacyTimestampToSql(r.created))
		setTimestamp(5, parseLegacyTimestampToSql(r.expires))
		setBoolean(6, r.used)
	}

	private fun copyProject(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO project (id, uuid, user_id, name, last_id, last_sync)
		VALUES (?, CAST(? AS UUID), ?, ?, ?, ?)
		""".trimIndent(),
		legacy.projectQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.id)
		setString(2, canonicalizeUuid(r.uuid))
		setLong(3, r.user_id)
		setString(4, r.name)
		setLong(5, r.last_id)
		setTimestamp(6, parseLegacyTimestampToSql(r.last_sync))
	}

	private fun copyProjectData(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		// Legacy `updated_at` was Unix-seconds INTEGER; promoted to TIMESTAMPTZ.
		"INSERT INTO project_data (user_id, project_id, content, hash, updated_at) VALUES (?, ?, ?, ?, ?)",
		legacy.projectDataQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setLong(2, r.project_id)
		setString(3, r.content)
		setString(4, r.hash)
		setTimestamp(5, Timestamp.from(java.time.Instant.ofEpochSecond(r.updated_at)))
	}

	private fun copyProjectAccess(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO project_access (id, project_id, access_password, expires_at, published_at)
		VALUES (?, ?, ?, ?, ?)
		""".trimIndent(),
		legacy.projectAccessQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.id)
		setLong(2, r.project_id)
		setString(3, r.access_password)
		setTimestamp(4, r.expires_at?.let { parseLegacyTimestampToSql(it) })
		setTimestamp(5, parseLegacyTimestampToSql(r.published_at))
	}

	private fun copyStoryEntity(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO story_entity (user_id, project_id, id, type, content, hash, cipher)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		""".trimIndent(),
		legacy.storyEntityQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setLong(2, r.project_id)
		setLong(3, r.id)
		setString(4, r.type)
		setString(5, r.content)
		setString(6, r.hash)
		setString(7, r.cipher)
	}

	private fun copyDeletedProject(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"INSERT INTO deleted_project (user_id, uuid) VALUES (?, CAST(? AS UUID))",
		legacy.deletedProjectQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setString(2, canonicalizeUuid(r.uuid))
	}

	private fun copyDeletedEntity(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO deleted_entity (user_id, project_id, entity_id, deleted_at)
		VALUES (?, ?, ?, ?)
		""".trimIndent(),
		legacy.deletedEntityQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setLong(2, r.project_id)
		setLong(3, r.entity_id)
		setTimestamp(4, parseLegacyTimestampToSql(r.deleted_at))
	}

	private fun copyServerConfig(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		// Legacy `updated_at` was Unix-seconds INTEGER; promoted to TIMESTAMPTZ.
		"INSERT INTO server_config (key, value, updated_at) VALUES (?, ?, ?)",
		legacy.serverConfigQueries.getAllForMigration().executeAsList(),
	) { r ->
		setString(1, r.key)
		setString(2, r.value_)
		setTimestamp(3, Timestamp.from(java.time.Instant.ofEpochSecond(r.updated_at)))
	}

	private fun copyWhiteList(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		// Legacy `date_added` was Unix-seconds INTEGER; promoted to TIMESTAMPTZ.
		"INSERT INTO white_list (email, date_added, reason) VALUES (?, ?, ?)",
		legacy.whiteListQueries.getAll().executeAsList(),
	) { r ->
		setString(1, r.email)
		setTimestamp(2, Timestamp.from(java.time.Instant.ofEpochSecond(r.date_added)))
		setString(3, r.reason)
	}

	private fun copyWritingActivity(legacy: LegacySqliteDatabase, conn: Connection): Int = copyTable(
		conn,
		"""
		INSERT INTO writing_activity (user_id, project_id, device_id, content)
		VALUES (?, ?, ?, ?)
		""".trimIndent(),
		legacy.writingActivityQueries.getAllForMigration().executeAsList(),
	) { r ->
		setLong(1, r.user_id)
		setLong(2, r.project_id)
		setString(3, r.device_id)
		setString(4, r.content)
	}

	private fun resetSequences(conn: Connection) {
		// 3-arg setval(seq, value, is_called): empty table → next nextval = 1.
		// Non-empty → next nextval = max(id)+1, so auto-id inserts don't collide.
		val tables = listOf("account", "project", "project_access", "password_reset_token")
		conn.createStatement().use { st ->
			for (t in tables) {
				st.execute(
					"""
					SELECT setval(
						pg_get_serial_sequence('$t', 'id'),
						COALESCE((SELECT MAX(id) FROM $t), 1),
						(SELECT MAX(id) IS NOT NULL FROM $t)
					)
					""".trimIndent()
				)
			}
		}
	}

	private fun renameLegacyToBackup(): Path {
		val stamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
		val backup = backupDir / "server.db.migrated-$stamp.bak"
		legacyPath.toFile().renameTo(backup.toFile())
		return backup
	}

	private fun parseLegacyTimestampToSql(text: String): Timestamp {
		val instant = parseLegacyTimestamp(text)
		return Timestamp.from(java.time.Instant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong()))
	}

	private fun canonicalizeUuid(text: String): String =
		runCatching { UUID.fromString(text).toString() }.getOrElse {
			error("Malformed UUID in legacy data: $text")
		}

	companion object {
		/** JDBC batch size for bulk inserts. */
		private const val BATCH_SIZE = 500

		/** CLI dry-run entry point. Returns 0 on parity pass, 1 on failure. */
		fun runDryRun(storage: StorageConfig, fileSystem: FileSystem): Int {
			val result = SqliteToPostgresMigrator(storage, fileSystem, dryRun = true).run()
			return when (result) {
				is Result.NoOp -> {
					println("--migrate-dry-run: no server.db found; nothing to migrate.")
					0
				}
				is Result.Success -> {
					println("--migrate-dry-run: parity check passed. Row counts:")
					result.rowCounts.forEach { (t, n) -> println("  $t: $n") }
					0
				}
				is Result.Aborted -> {
					println("--migrate-dry-run: ABORTED — ${result.reason}")
					1
				}
			}
		}
	}
}
