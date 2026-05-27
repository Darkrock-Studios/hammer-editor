package com.darkrockstudios.apps.hammer.e2e.util

/**
 * Translates the legacy SQLite-flavored fixture SQL files
 * (`server/src/test/resources/<Fixture>/server.db.sql`) into Postgres-
 * compatible SQL on the fly during test setup.
 *
 * The fixtures were authored against the pre-migration SQLite schema where:
 *   - Booleans were INTEGER 0/1.
 *   - Timestamps were either TEXT or Unix-epoch INTEGER.
 *   - `story_entity` originally lacked a `cipher` column (it was added in a
 *     SQLite migration but the older fixtures still omit it from `VALUES`).
 *
 * After the Postgres migration, the production schema uses real BOOLEAN,
 * TIMESTAMPTZ, and a 7-column `story_entity`. Rather than rewrite every
 * fixture file by hand (and lock them to v1 forever), this translator
 * patches each statement just enough to load cleanly into Postgres while
 * preserving the original test semantics.
 *
 * Targeted translations only — no general-purpose SQLite→Postgres rewriting.
 */
object LegacyFixtureSqlTranslator {

	/** Translate a single SQL statement. Whitespace-tolerant, table-aware. */
	fun translate(statement: String): String {
		val s = statement.trim().trimEnd(';').trim()
		val tableNamed = TABLE_PATTERN.find(s) ?: return statement
		val table = tableNamed.groupValues[1]
		return when (table) {
			"account" -> translateAccount(s)
			"white_list" -> translateWhitelist(s)
			"server_config" -> translateServerConfig(s)
			"story_entity" -> translateStoryEntity(s)
			else -> "$s;"
		}
	}

	// `account` columns:
	//   id, email, pen_name, password_hash, cipher_secret, created (text),
	//   is_admin (bool), last_sync (text), bio, email_verified (bool),
	//   community_member (bool).
	// Patch booleans at positions 7, 10, 11 (1-indexed) in the VALUES list.
	private fun translateAccount(s: String): String {
		val values = extractValuesList(s) ?: return "$s;"
		val patched = values.toMutableList()
		patched[6] = toBoolLiteral(patched[6]) // is_admin
		patched[9] = toBoolLiteral(patched[9]) // email_verified
		patched[10] = toBoolLiteral(patched[10]) // community_member
		return rebuildInsert("account", patched)
	}

	// `white_list` columns: email, date_added (legacy INTEGER epoch s), reason.
	private fun translateWhitelist(s: String): String {
		val values = extractValuesList(s) ?: return "$s;"
		val patched = values.toMutableList()
		patched[1] = epochSecondsToTimestamptz(patched[1])
		return rebuildInsert("white_list", patched)
	}

	// `server_config` columns: key, value, updated_at (legacy INTEGER epoch s).
	private fun translateServerConfig(s: String): String {
		val values = extractValuesList(s) ?: return "$s;"
		val patched = values.toMutableList()
		patched[2] = epochSecondsToTimestamptz(patched[2])
		return rebuildInsert("server_config", patched)
	}

	// `story_entity` columns: user_id, project_id, id, type, content, hash, cipher.
	// Older fixtures supply only 6 values (no cipher). Append NULL if so.
	private fun translateStoryEntity(s: String): String {
		val values = extractValuesList(s) ?: return "$s;"
		val patched = values.toMutableList()
		if (patched.size == 6) patched += "NULL"
		return rebuildInsert("story_entity", patched)
	}

	// --- helpers ---

	private val TABLE_PATTERN = Regex("""INSERT\s+INTO\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)

	private fun toBoolLiteral(raw: String): String = when (raw.trim()) {
		"0" -> "FALSE"
		"1" -> "TRUE"
		else -> raw
	}

	private fun epochSecondsToTimestamptz(raw: String): String {
		val trimmed = raw.trim()
		val asLong = trimmed.toLongOrNull() ?: return raw
		return "to_timestamp($asLong)"
	}

	/**
	 * Pulls the comma-separated VALUES list from an INSERT, respecting quoted
	 * strings. Returns null if the statement doesn't have a recognizable
	 * `VALUES(...)` form.
	 */
	private fun extractValuesList(s: String): List<String>? {
		val open = s.indexOf('(', s.indexOf("VALUES", ignoreCase = true).takeIf { it >= 0 } ?: return null)
		if (open < 0) return null
		val close = findMatchingParen(s, open)
		if (close < 0) return null
		val inner = s.substring(open + 1, close)
		return splitTopLevelCommas(inner).map { it.trim() }
	}

	private fun findMatchingParen(s: String, openIdx: Int): Int {
		var depth = 0
		var inSingle = false
		var i = openIdx
		while (i < s.length) {
			val c = s[i]
			if (c == '\'' && (i == 0 || s[i - 1] != '\\')) inSingle = !inSingle
			else if (!inSingle && c == '(') depth++
			else if (!inSingle && c == ')') {
				depth--
				if (depth == 0) return i
			}
			i++
		}
		return -1
	}

	private fun splitTopLevelCommas(s: String): List<String> {
		val out = mutableListOf<String>()
		var depth = 0
		var inSingle = false
		val cur = StringBuilder()
		for ((idx, c) in s.withIndex()) {
			when {
				c == '\'' && (idx == 0 || s[idx - 1] != '\\') -> {
					inSingle = !inSingle; cur.append(c)
				}
				!inSingle && c == '(' -> {
					depth++; cur.append(c)
				}
				!inSingle && c == ')' -> {
					depth--; cur.append(c)
				}
				!inSingle && depth == 0 && c == ',' -> {
					out += cur.toString(); cur.setLength(0)
				}
				else -> cur.append(c)
			}
		}
		if (cur.isNotEmpty()) out += cur.toString()
		return out
	}

	private fun rebuildInsert(table: String, values: List<String>): String =
		"INSERT INTO \"$table\" VALUES (${values.joinToString(", ") { it.trim() }});"
}
