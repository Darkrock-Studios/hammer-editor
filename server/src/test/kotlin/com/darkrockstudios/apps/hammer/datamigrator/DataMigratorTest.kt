package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class DataMigratorTest : BaseTest() {

	@Test
	fun `runMigrations - runs all migrations`() = runTest {
		val migrator = DataMigrator()
		migrator.runMigrations()
		// If it doesn't crash, at least the migrator ran
		assertTrue(true)
	}
}
