package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.ServerConfigKey
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.datamigrator.migrations.DataMigration
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataMigratorTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var configRepository: ConfigRepository

	private class CountingMigration(override val id: String) : DataMigration {
		var runs = 0
		override suspend fun migrate() {
			runs++
		}
	}

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		configRepository = ConfigRepository(ServerConfigDao(db))

		setupKoin()
	}

	private fun markerKey(id: String) = ServerConfigKey.boolean("datamigration_${id}_complete", false)

	@Test
	fun `runMigrations - runs an unmarked migration and sets its marker`() = runTest {
		val migration = CountingMigration("test_migration")
		val migrator = DataMigrator(configRepository).apply { addMigration(migration) }

		migrator.runMigrations()

		assertEquals(1, migration.runs)
		assertTrue(configRepository.get(markerKey(migration.id)))
	}

	@Test
	fun `runMigrations - skips a completed migration on later runs`() = runTest {
		val migration = CountingMigration("test_migration")
		val migrator = DataMigrator(configRepository).apply { addMigration(migration) }

		migrator.runMigrations()
		migrator.runMigrations()

		assertEquals(1, migration.runs)
	}

	@Test
	fun `runMigrations - a failing migration propagates and leaves its marker unset`() = runTest {
		val failing = object : DataMigration {
			override val id = "failing_migration"
			override suspend fun migrate() = error("migration failure")
		}
		val migrator = DataMigrator(configRepository).apply { addMigration(failing) }

		assertFailsWith<IllegalStateException> {
			migrator.runMigrations()
		}

		assertFalse(configRepository.get(markerKey(failing.id)))
	}
}
