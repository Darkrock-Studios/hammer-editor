package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Handles first-run bootstrap and version-tracking for the PostgreSQL schema.
 *
 * The `.sq` files declare columns that should logically be CITEXT or UUID as
 * plain TEXT (SqlDelight's Postgres dialect parser only knows a fixed set of
 * type names). After `Schema.create()` runs the CREATE TABLE statements, this
 * initializer:
 *   1. Installs the `citext` extension.
 *   2. Promotes the relevant columns to CITEXT / UUID.
 *   3. Records the SqlDelight schema version in a `_schema_version` table so we
 *      can drive `Schema.migrate(...)` on subsequent boots without relying on
 *      SQLite-only `PRAGMA user_version`.
 */
object PostgresSchemaInitializer {

	private const val VERSION_TABLE = "_schema_version"

	/**
	 * Run on every boot. Creates the schema on first run, otherwise migrates it
	 * forward to the current SqlDelight schema version.
	 */
	fun initialize(driver: SqlDriver) {
		ensureVersionTable(driver)
		val current = readSchemaVersion(driver)
		val target = ServerDatabase.Schema.version

		if (current == 0L) {
			// First-run: extension first so CREATE TABLE statements that reference
			// CITEXT (after the type-promotion below) are valid mid-transaction.
			driver.execute(null, "CREATE EXTENSION IF NOT EXISTS citext", 0)
			ServerDatabase.Schema.create(driver)
			promoteColumns(driver)
			writeSchemaVersion(driver, target)
		} else if (current < target) {
			ServerDatabase.Schema.migrate(driver, current, target)
			writeSchemaVersion(driver, target)
		}
	}

	private fun ensureVersionTable(driver: SqlDriver) {
		driver.execute(
			null,
			"CREATE TABLE IF NOT EXISTS $VERSION_TABLE (version BIGINT NOT NULL)",
			0,
		)
	}

	private fun readSchemaVersion(driver: SqlDriver): Long {
		return driver.executeQuery(
			identifier = null,
			sql = "SELECT version FROM $VERSION_TABLE LIMIT 1",
			mapper = { cursor ->
				QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
			},
			parameters = 0,
			binders = null,
		).value
	}

	private fun writeSchemaVersion(driver: SqlDriver, version: Long) {
		driver.execute(null, "DELETE FROM $VERSION_TABLE", 0)
		driver.execute(null, "INSERT INTO $VERSION_TABLE (version) VALUES ($version)", 0)
	}

	/**
	 * Promote columns we declared as TEXT in .sq to their richer native types.
	 * Idempotent — Postgres treats ALTER TYPE to the same type as a no-op.
	 */
	private fun promoteColumns(driver: SqlDriver) {
		// CITEXT — case-insensitive comparison + UNIQUE indexes.
		driver.execute(null, "ALTER TABLE account ALTER COLUMN email TYPE CITEXT", 0)
		driver.execute(null, "ALTER TABLE account ALTER COLUMN pen_name TYPE CITEXT", 0)
		driver.execute(null, "ALTER TABLE white_list ALTER COLUMN email TYPE CITEXT", 0)
		// Project name lookups (find-by-URL-slug, the community feed) were
		// case-insensitive in the SQLite era via LOWER(name)=LOWER(?); making
		// the column CITEXT preserves that and makes the (name, user_id)
		// uniqueness constraint case-insensitive too.
		driver.execute(null, "ALTER TABLE project ALTER COLUMN name TYPE CITEXT", 0)

		// UUID — 16-byte storage + format validation. SqlDelight binds the column
		// as a String at the API boundary, but Postgres accepts text input for
		// UUID columns via implicit cast.
		driver.execute(null, "ALTER TABLE project ALTER COLUMN uuid TYPE UUID USING uuid::uuid", 0)
		driver.execute(
			null,
			"ALTER TABLE deleted_project ALTER COLUMN uuid TYPE UUID USING uuid::uuid",
			0,
		)
	}
}
