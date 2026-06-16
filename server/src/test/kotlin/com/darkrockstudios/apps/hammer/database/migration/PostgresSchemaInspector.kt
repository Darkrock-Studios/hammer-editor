package com.darkrockstudios.apps.hammer.database.migration

import java.sql.Connection

/**
 * Introspects a PostgreSQL database's `public` schema into a structural model
 * that can be compared for equality regardless of how the schema was built.
 *
 * Comparison is intentionally column-order-independent: an upgraded database
 * appends migration-added columns at the end (via ALTER TABLE) while a fresh
 * install declares them inline, so physical ordinal position legitimately differs
 * and is not a real schema difference. Everything that does matter — the set of
 * columns with their types/nullability/defaults, plus all constraints and indexes
 * (PK, FK, UNIQUE, CHECK) by their deterministic names — is captured and compared.
 *
 * The `_schema_version` bookkeeping table is excluded; only a fresh install
 * creates it, and it carries no application schema.
 */
internal object PostgresSchemaInspector {

	fun inspect(connection: Connection): SchemaModel = SchemaModel(
		columns = queryColumns(connection),
		constraints = queryConstraints(connection),
		indexes = queryIndexes(connection),
	)

	private fun queryColumns(connection: Connection): Map<String, String> {
		val sql = """
			SELECT table_name, column_name, udt_name, is_nullable, COALESCE(column_default, '') AS column_default
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name <> '_schema_version'
		""".trimIndent()
		val out = sortedMapOf<String, String>()
		connection.prepareStatement(sql).executeQuery().use { rs ->
			while (rs.next()) {
				val key = "${rs.getString("table_name")}.${rs.getString("column_name")}"
				out[key] = "udt=${rs.getString("udt_name")} " +
					"nullable=${rs.getString("is_nullable")} " +
					"default=${rs.getString("column_default")}"
			}
		}
		return out
	}

	private fun queryConstraints(connection: Connection): Map<String, String> {
		val sql = """
			SELECT cls.relname AS table_name, con.conname, pg_get_constraintdef(con.oid) AS def
			FROM pg_constraint con
			JOIN pg_class cls ON cls.oid = con.conrelid
			JOIN pg_namespace ns ON ns.oid = cls.relnamespace
			WHERE ns.nspname = 'public' AND cls.relname <> '_schema_version'
		""".trimIndent()
		val out = sortedMapOf<String, String>()
		connection.prepareStatement(sql).executeQuery().use { rs ->
			while (rs.next()) {
				val key = "${rs.getString("table_name")}.${rs.getString("conname")}"
				out[key] = rs.getString("def")
			}
		}
		return out
	}

	private fun queryIndexes(connection: Connection): Map<String, String> {
		val sql = """
			SELECT tablename, indexname, indexdef
			FROM pg_indexes
			WHERE schemaname = 'public' AND tablename <> '_schema_version'
		""".trimIndent()
		val out = sortedMapOf<String, String>()
		connection.prepareStatement(sql).executeQuery().use { rs ->
			while (rs.next()) {
				val key = "${rs.getString("tablename")}.${rs.getString("indexname")}"
				// Index definitions reference the schema-qualified table; drop the
				// qualifier so the comparison is about structure, not catalog naming.
				out[key] = rs.getString("indexdef").replace("public.", "")
			}
		}
		return out
	}
}

/** Comparable structural snapshot of a Postgres `public` schema. */
internal data class SchemaModel(
	val columns: Map<String, String>,
	val constraints: Map<String, String>,
	val indexes: Map<String, String>,
) {
	/**
	 * Returns a human-readable list of every difference from [expected], or an
	 * empty list when the two schemas are structurally identical.
	 */
	fun diffFrom(expected: SchemaModel): List<String> = buildList {
		addAll(diffMap("column", expected.columns, columns))
		addAll(diffMap("constraint", expected.constraints, constraints))
		addAll(diffMap("index", expected.indexes, indexes))
	}

	private fun diffMap(label: String, expected: Map<String, String>, actual: Map<String, String>): List<String> =
		buildList {
			for (key in (expected.keys + actual.keys).sorted()) {
				val e = expected[key]
				val a = actual[key]
				when {
					e == null -> add("unexpected $label `$key`: $a")
					a == null -> add("missing $label `$key`: expected $e")
					e != a -> add("$label `$key` differs:\n    expected: $e\n    actual:   $a")
				}
			}
		}
}
