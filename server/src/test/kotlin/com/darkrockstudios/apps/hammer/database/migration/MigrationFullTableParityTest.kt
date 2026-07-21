package com.darkrockstudios.apps.hammer.database.migration

import com.darkrockstudios.apps.hammer.database.EmbeddedPostgresDatabase
import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the migrator against the [MigrationFixtureBuilder.tiny] fixture and
 * verifies that every table the migrator handles ends up in PostgreSQL with
 * the expected row count, plus a spot-check on a representative row from
 * each table to catch column-by-column corruption.
 */
class MigrationFullTableParityTest {

	@Test
	fun `tiny fixture migrates with every table populated and spot-checked`(@TempDir tempDir: Path) {
		MigrationTestSupport.withRedirectedHome(tempDir) {
			val legacyDb = MigrationTestSupport.legacyDbPath(tempDir)
			val expected = MigrationFixtureBuilder.tiny(legacyDb.absolutePath).expectedRowCounts

			val storage = MigrationTestSupport.storageFor()
			val migrator = SqliteToPostgresMigrator(storage, FileSystem.SYSTEM, dryRun = false)
			val result = migrator.run()

			val success = result as? SqliteToPostgresMigrator.Result.Success
			assertNotNull(success, "migration should succeed; got $result")

			// Row counts as reported by the migrator match the fixture.
			for ((table, count) in expected) {
				assertEquals(count, success.rowCounts[table], "row count mismatch for $table")
			}

			// Re-open the Postgres database and spot-check that columns round-tripped.
			MigrationTestSupport.withEmbeddedPostgres(storage) { db ->
				assertAccount(db)
				assertProject(db)
				assertStoryEntities(db, success.rowCounts["story_entity"]!!)
				assertProjectData(db)
				assertProjectAccess(db)
				assertAuthTokens(db)
				assertPasswordResetToken(db)
				assertDeletedProject(db)
				assertDeletedEntity(db)
				assertWhiteList(db)
				assertServerConfig(db)
				assertWritingActivity(db)
				assertSequencesAdvanced(db)
			}
		}
	}

	private fun assertAccount(db: EmbeddedPostgresDatabase) {
		val q = db.serverDatabase.accountQueries
		val author = q.findAccount("author@example.com").executeAsOne()
		assertEquals(2L, author.id)
		assertEquals("Ada Lovelace", author.pen_name)
		assertEquals("Writer.", author.bio)
		assertTrue(author.community_member, "community_member flag preserved")
		assertTrue(author.email_verified, "email_verified flag preserved")
		// "2024-02-15 09:00:00" parsed as UTC; catches local-zone parsing of legacy timestamps.
		assertEquals(1_707_987_600L, author.created.epochSeconds, "created should round-trip as UTC")

		// Schema-level: confirm the email column actually is CITEXT (silent if
		// missed — comparisons just stay case-sensitive).
		val driver = db.driver
		val conn = driver.getConnection()
		try {
			conn.prepareStatement(
				"SELECT udt_name FROM information_schema.columns " +
					"WHERE table_name='account' AND column_name='email'"
			).executeQuery().use { rs ->
				assertTrue(rs.next(), "email column should exist")
				assertEquals("citext", rs.getString("udt_name"), "email should be CITEXT")
			}
		} finally {
			driver.closeConnection(conn)
		}

		// Query-level: the production `findAccount` uses CAST(? AS CITEXT) so
		// the case-insensitive comparison actually engages.
		val authorCi = q.findAccount("AUTHOR@example.com").executeAsOneOrNull()
		assertNotNull(authorCi, "CITEXT email lookup is case-insensitive")
		assertEquals(2L, authorCi.id)
	}

	private fun assertProject(db: EmbeddedPostgresDatabase) {
		val q = db.serverDatabase.projectQueries
		val uuid = "11111111-1111-1111-1111-111111111111"
		val project = q.getProject(2, uuid).executeAsOne()
		assertEquals("First Novel", project.name)
		assertEquals(4L, project.last_id)
		// UUID column round-trip — value preserved canonicalized.
		assertEquals(uuid, project.uuid)
	}

	private fun assertStoryEntities(db: EmbeddedPostgresDatabase, expected: Int) {
		val all = db.serverDatabase.storyEntityQueries.getAllEntities(2, 1).executeAsList()
		assertEquals(expected, all.size)
		val scene = all.single { it.id == 1L }
		assertEquals("scene", scene.type)
		assertEquals("Once upon a time...", scene.content)
		val note = all.single { it.id == 2L }
		assertEquals("AES/GCM/NoPadding", note.cipher)
	}

	private fun assertProjectData(db: EmbeddedPostgresDatabase) {
		val row = db.serverDatabase.projectDataQueries.getForProject(2, 1).executeAsOne()
		assertEquals("{\"sync\":\"blob\"}", row.content)
		assertEquals("pd-hash", row.hash)
		// Epoch seconds 1_717_200_000 → 2024-06-01 00:00:00 UTC
		assertEquals(1_717_200_000L, row.updated_at.epochSeconds)
	}

	private fun assertProjectAccess(db: EmbeddedPostgresDatabase) {
		val all = db.serverDatabase.projectAccessQueries.getAllAccessForProject(1).executeAsList()
		assertEquals(2, all.size)
		val public = all.single { it.access_password == null }
		assertEquals(null, public.expires_at)
		val priv = all.single { it.access_password == "let-me-in" }
		// "2099-12-31 23:59:59" parsed as UTC.
		assertEquals(4_102_444_799L, priv.expires_at?.epochSeconds)
	}

	private fun assertAuthTokens(db: EmbeddedPostgresDatabase) {
		val q = db.serverDatabase.authTokenQueries
		val active = q.getTokenByInstallId(2, "device-A").executeAsOne()
		assertEquals("active-token", active.token)
		val expired = q.getTokenByInstallId(2, "device-B").executeAsOne()
		assertEquals("expired-token", expired.token)
		// "2019-01-01 00:00:00" / "2020-01-01 00:00:00" parsed as UTC — a token that
		// migrates with a shifted expiry would wrongly extend or cut short its life.
		assertEquals(1_546_300_800L, expired.created.epochSeconds)
		assertEquals(1_577_836_800L, expired.expires.epochSeconds)
	}

	private fun assertPasswordResetToken(db: EmbeddedPostgresDatabase) {
		val row = db.serverDatabase.passwordResetTokenQueries
			.getTokenByToken("reset-token-1").executeAsOne()
		assertEquals(2L, row.user_id)
		assertEquals(false, row.used)
	}

	private fun assertDeletedProject(db: EmbeddedPostgresDatabase) {
		val uuid = "22222222-2222-2222-2222-222222222222"
		val rows = db.serverDatabase.deletedProjectQueries.getDeletedProjects(2).executeAsList()
		assertEquals(1, rows.size)
		assertEquals(uuid, rows.single().uuid)
		// Confirm the column type really is UUID (parses without throwing).
		UUID.fromString(rows.single().uuid)
	}

	private fun assertDeletedEntity(db: EmbeddedPostgresDatabase) {
		val rows = db.serverDatabase.deletedEntityQueries.getDeletedEntities(2, 1).executeAsList()
		assertEquals(1, rows.size)
		assertEquals(5L, rows.single().entity_id)
	}

	private fun assertWhiteList(db: EmbeddedPostgresDatabase) {
		val rows = db.serverDatabase.whiteListQueries.getAll().executeAsList()
		assertEquals(1, rows.size)
		assertEquals("whitelisted@example.com", rows.single().email)
		assertEquals("invited by admin", rows.single().reason)
		assertEquals(1_700_000_000L, rows.single().date_added.epochSeconds)
	}

	private fun assertServerConfig(db: EmbeddedPostgresDatabase) {
		val value = db.serverDatabase.serverConfigQueries
			.getConfig("whitelist_enabled").executeAsOne()
		assertEquals("true", value)
	}

	private fun assertWritingActivity(db: EmbeddedPostgresDatabase) {
		val rows = db.serverDatabase.writingActivityQueries
			.getAllForProject(2, 1).executeAsList()
		assertEquals(1, rows.size)
		assertEquals("device-A", rows.single().device_id)
		assertEquals("{\"words\":1234}", rows.single().content)
	}

	/**
	 * After migration the BIGSERIAL sequences should be set so the next
	 * auto-generated id is `max(id) + 1`, never colliding with migrated rows.
	 */
	private fun assertSequencesAdvanced(db: EmbeddedPostgresDatabase) {
		val newId = db.serverDatabase.accountQueries.transactionWithResult {
			db.serverDatabase.accountQueries.createAccount(
				email = "new@example.com",
				password_hash = "h",
				cipher_secret = "s",
				is_admin = false,
			)
			db.serverDatabase.accountQueries.findAccount("new@example.com").executeAsOne().id
		}
		assertEquals(3L, newId, "next account id should be max(id)+1=3")
	}
}
