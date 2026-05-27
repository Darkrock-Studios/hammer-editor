package com.darkrockstudios.apps.hammer.database.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.darkrockstudios.apps.hammer.database.legacy.LegacySqliteDatabase
import java.util.Properties
import java.util.UUID

/**
 * Builds a legacy SQLite database (v5 schema) populated with realistic data
 * covering every table the migrator handles. Used by the migration test suite
 * as the "before" state to migrate from.
 *
 * Three size factories:
 *  - [tiny]: enough to exercise every table with at least one row, including
 *    NULLs and case-variant data. Fast — runs in well under a second.
 *  - [realistic]: tens of users, hundreds of projects, thousands of entities;
 *    suitable for the default parity test.
 *  - [withConstraintViolations]: deliberately violates one or more of the v1
 *    PostgreSQL CHECK constraints / UUID validations so failure-mode tests
 *    can assert that the migrator aborts cleanly.
 */
object MigrationFixtureBuilder {

	/** Open a freshly-created legacy SQLite database at [dbPath]. */
	private fun open(dbPath: String): Pair<JdbcSqliteDriver, LegacySqliteDatabase> {
		val driver = JdbcSqliteDriver(
			url = "jdbc:sqlite:$dbPath",
			properties = Properties().apply { put("foreign_keys", "false") },
		)
		LegacySqliteDatabase.Schema.create(driver)
		return driver to LegacySqliteDatabase(driver)
	}

	/**
	 * Create an empty v5 legacy SQLite database at [dbPath] and run [seed]
	 * against it. Used by failure-mode tests that want to plant specific bad
	 * data without going through the [tiny] preset.
	 */
	fun withSchema(dbPath: String, seed: (LegacySqliteDatabase) -> Unit) {
		val (driver, db) = open(dbPath)
		try {
			seed(db)
		} finally {
			driver.close()
		}
	}

	/**
	 * Tiny fixture covering every table with at least one row. Used by the
	 * primary parity test. The data exercises:
	 *  - Two accounts (admin + community member, with + without pen_name + bio).
	 *  - One project with a story_entity in each of the four entity types, plus
	 *    a deleted_entity, encrypted (cipher) and unencrypted rows.
	 *  - A project_data sync blob.
	 *  - Two project_access rows: one public (NULL password, NULL expiry), one
	 *    password-protected with a future expiry.
	 *  - A deleted_project row.
	 *  - A live and an expired auth_token, plus an unused password_reset_token.
	 *  - One white_list entry and one server_config row.
	 */
	fun tiny(dbPath: String): TinyFixture {
		val (driver, db) = open(dbPath)
		try {
			// --- accounts ---
			db.accountQueries.testInsertAccount(
				email = "admin@example.com",
				password_hash = "\$argon2id\$admin",
				cipher_secret = "secret-admin",
				is_admin = true,
				created = "2024-01-01 00:00:00",
				last_sync = "2024-06-01 12:30:00",
			)
			db.accountQueries.testInsertAccount(
				email = "author@example.com",
				password_hash = "\$argon2id\$author",
				cipher_secret = "secret-author",
				is_admin = false,
				created = "2024-02-15 09:00:00",
				last_sync = "2024-06-01 12:30:00",
			)
			// Set the community-only bits via update; testInsertAccount doesn't
			// cover them. Pen name + bio populated for account 2.
			driver.execute(null, "UPDATE account SET pen_name='Ada Lovelace', bio='Writer.', community_member=1, email_verified=1 WHERE id=2", 0)

			// --- projects ---
			val projectUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
			db.projectQueries.insertProject(
				userId = 2,
				name = "First Novel",
				uuid = projectUuid.toString(),
				lastSync = "2024-06-01 12:00:00",
				lastId = 4,
			)
			val deletedProjectUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
			db.deletedProjectQueries.addDeletedProject(userId = 2, uuid = deletedProjectUuid.toString())

			// --- story entities, one per type ---
			fun entity(id: Long, type: String, content: String, cipher: String? = null) {
				db.storyEntityQueries.insertNew(
					userId = 2,
					projectId = 1,
					id = id,
					type = type,
					content = content,
					hash = "h-$id",
					cipher = cipher,
				)
			}
			entity(1, "scene", "Once upon a time...")
			entity(2, "note", "Plot idea: turn the protagonist into a duck.", cipher = "AES/GCM/NoPadding")
			entity(3, "encyclopedia_entry", "Duck: a waterfowl.")
			entity(4, "timeline_event", "Year 1: protagonist transformed.")
			db.deletedEntityQueries.markEntityDeleted(userId = 2, projectId = 1, id = 5)

			// --- project_data ---
			db.projectDataQueries.upsert(
				userId = 2,
				projectId = 1,
				content = "{\"sync\":\"blob\"}",
				hash = "pd-hash",
				updatedAt = 1_717_200_000L,
			)

			// --- project_access: one public, one password-protected ---
			db.projectAccessQueries.insertAccess(
				project_id = 1,
				access_password = null,
				expires_at = null,
			)
			db.projectAccessQueries.insertAccess(
				project_id = 1,
				access_password = "let-me-in",
				expires_at = "2099-12-31 23:59:59",
			)

			// --- auth_token: live + expired ---
			db.authTokenQueries.setToken(
				userId = 2,
				installId = "device-A",
				token = "active-token",
				refresh = "refresh-A",
				expires = "2099-12-31 23:59:59",
			)
			// "Expired" in the sense that expires is in the past — but created
			// must precede expires (the v1 schema enforces this via CHECK).
			driver.execute(
				null,
				"INSERT INTO auth_token (user_id, install_id, token, refresh, created, expires) " +
					"VALUES (2, 'device-B', 'expired-token', 'refresh-B', '2019-01-01 00:00:00', '2020-01-01 00:00:00')",
				0,
			)

			// --- password_reset_token ---
			db.passwordResetTokenQueries.createToken(
				user_id = 2,
				token = "reset-token-1",
				expires = "2099-12-31 23:59:59",
			)

			// --- white_list ---
			driver.execute(
				null,
				"INSERT INTO white_list (email, date_added, reason) VALUES ('whitelisted@example.com', 1700000000, 'invited by admin')",
				0,
			)

			// --- server_config ---
			driver.execute(
				null,
				"INSERT INTO server_config (key, value, updated_at) VALUES ('whitelist_enabled', 'true', 1717200000)",
				0,
			)

			// --- writing_activity ---
			db.writingActivityQueries.upsertDeviceLog(
				userId = 2,
				projectId = 1,
				deviceId = "device-A",
				content = "{\"words\":1234}",
			)
		} finally {
			driver.close()
		}
		return TinyFixture(
			expectedRowCounts = mapOf(
				"account" to 2,
				"auth_token" to 2,
				"password_reset_token" to 1,
				"project" to 1,
				"project_data" to 1,
				"project_access" to 2,
				"story_entity" to 4,
				"deleted_project" to 1,
				"deleted_entity" to 1,
				"server_config" to 1,
				"white_list" to 1,
				"writing_activity" to 1,
			),
		)
	}

	data class TinyFixture(val expectedRowCounts: Map<String, Int>)
}
