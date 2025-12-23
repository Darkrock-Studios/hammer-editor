package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.FileResourcesUtils
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class DataMigratorTest : BaseTest() {

	@Test
	fun `runMigrations - runs all migrations`() = runTest {
		val fakeFileSystem = FakeFileSystem()
		val testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		val migrator = DataMigrator(fakeFileSystem, testDatabase)
		migrator.runMigrations()

		// If it doesn't crash, at least the migrator ran
		assertTrue(true)
	}

	@Test
	fun `runMigrations - runs migrations - Schema 3 to 4`() = runTest {
		val fakeFileSystem = FakeFileSystem()
		val testDatabase = SqliteTestDatabase(createSchema = false, enforceForeignKeys = false)
		testDatabase.initialize()

		// Populate DB with test data
		FileResourcesUtils.setupDatabase("OldSchemas/3/".toPath(), testDatabase)

		val migrator = DataMigrator(fakeFileSystem, testDatabase)
		migrator.runMigrations()

		// If it doesn't crash, at least the migrator ran
		assertTrue(true)
	}
}
