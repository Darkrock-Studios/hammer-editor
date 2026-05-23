package com.darkrockstudios.apps.hammer.database.migration

import java.sql.Connection

/** A mismatch in row counts between SQLite and Postgres for one table. */
data class CountMismatch(
	val table: String,
	val sqliteCount: Int,
	val postgresCount: Int,
) {
	override fun toString(): String =
		"$table: sqlite=$sqliteCount, postgres=$postgresCount"
}

/**
 * Row-count parity check used in-transaction by the migrator. Runs against
 * the open JDBC [Connection] so it sees uncommitted rows under READ COMMITTED.
 * Short-circuits at the first mismatch.
 *
 * Pairs with the steady-state DAO tests, which exercise actual column data;
 * the migrator itself enforces shape via column types and CHECK constraints,
 * so a count match is a strong signal nothing was silently dropped.
 */
object MigrationParityChecker {
	/**
	 * For each `(table → copiedRowCount)` in [copied], assert Postgres holds the
	 * same number of rows. Returns the first mismatch, or `null` on full parity.
	 */
	fun checkAllCounts(copied: Map<String, Int>, conn: Connection): CountMismatch? {
		for ((table, expected) in copied) {
			val actual = countPostgres(conn, table)
			if (expected != actual) return CountMismatch(table, expected, actual)
		}
		return null
	}

	private fun countPostgres(conn: Connection, table: String): Int =
		conn.prepareStatement("SELECT COUNT(*) FROM $table").executeQuery().use {
			it.next(); it.getInt(1)
		}
}
