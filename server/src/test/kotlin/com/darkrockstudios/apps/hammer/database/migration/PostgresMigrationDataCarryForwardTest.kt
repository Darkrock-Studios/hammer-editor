package com.darkrockstudios.apps.hammer.database.migration

import com.darkrockstudios.apps.hammer.database.ServerDatabase
import com.darkrockstudios.apps.hammer.database.buildServerDatabase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the data half of the production upgrade path: rows written under the v1
 * schema must still be present and intact after migrating all the way to the
 * latest version. Reads go through the current generated queries, so this also
 * proves the upgraded schema stays query-compatible with the live data layer.
 */
class PostgresMigrationDataCarryForwardTest {

	private val latest: Long = ServerDatabase.Schema.version

	@Test
	fun `v1 data survives migration to the latest schema`() {
		val db = PostgresUpgradeTestSupport.freshDatabase()
		PostgresUpgradeTestSupport.applyScript(db.driver, PostgresUpgradeTestSupport.loadBaseline(1))
		PostgresUpgradeTestSupport.applyScript(db.driver, SEED_V1_DATA)

		ServerDatabase.Schema.migrate(db.driver, 1L, latest)

		val sd = buildServerDatabase(db.driver)
		assertAccounts(sd)
		assertProject(sd)
		assertStoryEntities(sd)
		assertProjectData(sd)
		assertProjectAccess(sd)
		assertAuthTokens(sd)
		assertPasswordResetToken(sd)
		assertDeletedProject(sd)
		assertDeletedEntity(sd)
		assertWhiteList(sd)
		assertServerConfig(sd)
		assertWritingActivity(sd)
		assertMigrationAddedColumnDefault(db)
	}

	private fun assertAccounts(sd: ServerDatabase) {
		val author = sd.accountQueries.findAccount("author@example.com").executeAsOne()
		assertEquals(2L, author.id)
		assertEquals("Ada Lovelace", author.pen_name)
		assertEquals("Writer.", author.bio)
		assertTrue(author.community_member, "community_member preserved")
		assertTrue(author.email_verified, "email_verified preserved")

		// CITEXT promotion survived: case-insensitive email lookup still resolves.
		val ci = sd.accountQueries.findAccount("AUTHOR@EXAMPLE.COM").executeAsOneOrNull()
		assertNotNull(ci, "email lookup should be case-insensitive after migration")
		assertEquals(2L, ci.id)
	}

	private fun assertProject(sd: ServerDatabase) {
		val uuid = "11111111-1111-1111-1111-111111111111"
		val project = sd.projectQueries.getProject(2, uuid).executeAsOne()
		assertEquals("First Novel", project.name)
		assertEquals(4L, project.last_id)
		assertEquals(uuid, project.uuid)
	}

	private fun assertStoryEntities(sd: ServerDatabase) {
		val all = sd.storyEntityQueries.getAllEntities(2, 1).executeAsList()
		assertEquals(4, all.size)
		assertEquals("Once upon a time...", all.single { it.id == 1L }.content)
		assertEquals("AES/GCM/NoPadding", all.single { it.id == 2L }.cipher)
	}

	private fun assertProjectData(sd: ServerDatabase) {
		val row = sd.projectDataQueries.getForProject(2, 1).executeAsOne()
		assertEquals("{\"sync\":\"blob\"}", row.content)
		assertEquals("pd-hash", row.hash)
		assertEquals(1_717_200_000L, row.updated_at.epochSeconds)
	}

	private fun assertProjectAccess(sd: ServerDatabase) {
		val all = sd.projectAccessQueries.getAllAccessForProject(1).executeAsList()
		assertEquals(2, all.size)
		assertEquals(1, all.count { it.access_password == null })
		assertNotNull(all.single { it.access_password == "let-me-in" }.expires_at)
	}

	private fun assertAuthTokens(sd: ServerDatabase) {
		assertEquals("active-token", sd.authTokenQueries.getTokenByInstallId(2, "device-A").executeAsOne().token)
		assertEquals("expired-token", sd.authTokenQueries.getTokenByInstallId(2, "device-B").executeAsOne().token)
	}

	private fun assertPasswordResetToken(sd: ServerDatabase) {
		val row = sd.passwordResetTokenQueries.getTokenByToken("reset-token-1").executeAsOne()
		assertEquals(2L, row.user_id)
		assertEquals(false, row.used)
	}

	private fun assertDeletedProject(sd: ServerDatabase) {
		val rows = sd.deletedProjectQueries.getDeletedProjects(2).executeAsList()
		assertEquals(1, rows.size)
		assertEquals("22222222-2222-2222-2222-222222222222", rows.single().uuid)
	}

	private fun assertDeletedEntity(sd: ServerDatabase) {
		val rows = sd.deletedEntityQueries.getDeletedEntities(2, 1).executeAsList()
		assertEquals(1, rows.size)
		assertEquals(5L, rows.single().entity_id)
	}

	private fun assertWhiteList(sd: ServerDatabase) {
		val rows = sd.whiteListQueries.getAll().executeAsList()
		assertEquals(1, rows.size)
		assertEquals("whitelisted@example.com", rows.single().email)
		assertEquals("invited by admin", rows.single().reason)
		assertEquals(1_700_000_000L, rows.single().date_added.epochSeconds)
	}

	private fun assertServerConfig(sd: ServerDatabase) {
		assertEquals("true", sd.serverConfigQueries.getConfig("whitelist_enabled").executeAsOne())
	}

	private fun assertWritingActivity(sd: ServerDatabase) {
		val rows = sd.writingActivityQueries.getAllForProject(2, 1).executeAsList()
		assertEquals(1, rows.size)
		assertEquals("device-A", rows.single().device_id)
		assertEquals("{\"words\":1234}", rows.single().content)
	}

	/**
	 * Migration 3 adds `error_log.status INTEGER NOT NULL DEFAULT 500`. Confirm a
	 * row inserted without it on the migrated schema picks up that default.
	 */
	private fun assertMigrationAddedColumnDefault(db: IsolatedDatabase) {
		db.withConnection { conn ->
			conn.createStatement().use { st ->
				st.execute(
					"INSERT INTO error_log (fingerprint, exception_type, first_seen, last_seen) " +
						"VALUES ('fp-1', 'java.lang.RuntimeException', NOW(), NOW())"
				)
			}
			conn.prepareStatement("SELECT status FROM error_log WHERE fingerprint = 'fp-1'").executeQuery().use { rs ->
				assertTrue(rs.next(), "error_log row should exist")
				assertEquals(500, rs.getInt("status"), "status should default to 500 after migration")
			}
		}
	}

	private companion object {
		val SEED_V1_DATA = """
			INSERT INTO account (id, email, password_hash, cipher_secret, is_admin, created, last_sync)
			VALUES (1, 'admin@example.com', 'hash-admin', 'secret-admin', TRUE, '2024-01-01 00:00:00Z', '2024-06-01 12:30:00Z');

			INSERT INTO account (id, email, pen_name, password_hash, cipher_secret, is_admin, bio, community_member, email_verified, created, last_sync)
			VALUES (2, 'author@example.com', 'Ada Lovelace', 'hash-author', 'secret-author', FALSE, 'Writer.', TRUE, TRUE, '2024-02-15 09:00:00Z', '2024-06-01 12:30:00Z');

			INSERT INTO project (id, uuid, user_id, name, last_id, last_sync)
			VALUES (1, '11111111-1111-1111-1111-111111111111', 2, 'First Novel', 4, '2024-06-01 12:00:00Z');

			INSERT INTO deleted_project (user_id, uuid)
			VALUES (2, '22222222-2222-2222-2222-222222222222');

			INSERT INTO story_entity (user_id, project_id, id, type, content, hash, cipher)
			VALUES (2, 1, 1, 'scene', 'Once upon a time...', 'h-1', NULL);
			INSERT INTO story_entity (user_id, project_id, id, type, content, hash, cipher)
			VALUES (2, 1, 2, 'note', 'Plot idea.', 'h-2', 'AES/GCM/NoPadding');
			INSERT INTO story_entity (user_id, project_id, id, type, content, hash, cipher)
			VALUES (2, 1, 3, 'encyclopedia_entry', 'Duck: a waterfowl.', 'h-3', NULL);
			INSERT INTO story_entity (user_id, project_id, id, type, content, hash, cipher)
			VALUES (2, 1, 4, 'timeline_event', 'Year 1.', 'h-4', NULL);

			INSERT INTO deleted_entity (user_id, project_id, entity_id)
			VALUES (2, 1, 5);

			INSERT INTO project_data (user_id, project_id, content, hash, updated_at)
			VALUES (2, 1, '{"sync":"blob"}', 'pd-hash', to_timestamp(1717200000));

			INSERT INTO project_access (project_id, access_password, expires_at)
			VALUES (1, NULL, NULL);
			INSERT INTO project_access (project_id, access_password, expires_at)
			VALUES (1, 'let-me-in', '2099-12-31 23:59:59Z');

			INSERT INTO auth_token (user_id, install_id, token, refresh, created, expires)
			VALUES (2, 'device-A', 'active-token', 'refresh-A', '2024-01-01 00:00:00Z', '2099-12-31 23:59:59Z');
			INSERT INTO auth_token (user_id, install_id, token, refresh, created, expires)
			VALUES (2, 'device-B', 'expired-token', 'refresh-B', '2019-01-01 00:00:00Z', '2020-01-01 00:00:00Z');

			INSERT INTO password_reset_token (user_id, token, expires)
			VALUES (2, 'reset-token-1', '2099-12-31 23:59:59Z');

			INSERT INTO white_list (email, date_added, reason)
			VALUES ('whitelisted@example.com', to_timestamp(1700000000), 'invited by admin');

			INSERT INTO server_config (key, value)
			VALUES ('whitelist_enabled', 'true');

			INSERT INTO writing_activity (user_id, project_id, device_id, content)
			VALUES (2, 1, 'device-A', '{"words":1234}');
		""".trimIndent()
	}
}
