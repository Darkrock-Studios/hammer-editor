package com.darkrockstudios.apps.hammer.database.migration

import com.darkrockstudios.apps.hammer.database.PostgresSchemaInitializer
import com.darkrockstudios.apps.hammer.database.ServerDatabase
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Guards the production upgrade path: a database created at an older schema
 * version and migrated forward must end up structurally identical to a brand-new
 * fresh install. If a `.sq` change ships without a matching `.sqm` migration (or
 * the two drift apart), an upgraded production database silently diverges from a
 * fresh one — this test fails before that reaches production.
 *
 * Every supported start version is exercised. The version-1 baseline is the only
 * checked-in snapshot ([PostgresUpgradeTestSupport.loadBaseline]); intermediate
 * start versions are reached by migrating the baseline partway forward, so adding
 * a new schema version needs no new fixture — the loop picks it up from
 * [ServerDatabase.Schema.version].
 */
class PostgresMigrationSchemaParityTest {

	private val latest: Long = ServerDatabase.Schema.version

	@Test
	fun `every supported upgrade path matches a fresh install`() {
		val fresh = freshInstallSchema()

		for (startVersion in 1L until latest) {
			val migrated = migratedSchema(startVersion)
			val diffs = migrated.diffFrom(fresh)
			assertTrue(
				diffs.isEmpty(),
				buildString {
					appendLine("Upgrade from v$startVersion to v$latest does not match a fresh v$latest install:")
					diffs.forEach { appendLine("  - $it") }
				},
			)
		}
	}

	/** Schema produced by the real first-boot initializer on an empty database. */
	private fun freshInstallSchema(): SchemaModel {
		val db = PostgresUpgradeTestSupport.freshDatabase()
		PostgresSchemaInitializer.initialize(db.driver)
		return db.withConnection(PostgresSchemaInspector::inspect)
	}

	/** Schema produced by starting at [startVersion] and migrating to the latest. */
	private fun migratedSchema(startVersion: Long): SchemaModel {
		val db = PostgresUpgradeTestSupport.freshDatabase()
		PostgresUpgradeTestSupport.applyScript(db.driver, PostgresUpgradeTestSupport.loadBaseline(1))
		if (startVersion > 1L) {
			ServerDatabase.Schema.migrate(db.driver, 1L, startVersion)
		}
		ServerDatabase.Schema.migrate(db.driver, startVersion, latest)
		return db.withConnection(PostgresSchemaInspector::inspect)
	}
}
