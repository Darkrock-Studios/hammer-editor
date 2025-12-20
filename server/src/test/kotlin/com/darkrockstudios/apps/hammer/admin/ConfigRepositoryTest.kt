package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigRepositoryTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase
	private lateinit var dao: ServerConfigDao

	private val MAX_UPLOAD_SIZE = ServerConfigKey.int("max_upload_size_mb", 10)
	private val MAINTENANCE_MODE = ServerConfigKey.boolean("maintenance_mode", false)
	private val SERVER_NAME = ServerConfigKey.string("server_name", "Hammer Server")

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SqliteTestDatabase()
		db.initialize()
		dao = ServerConfigDao(db)

		setupKoin()
	}

	private fun createRepo() = ConfigRepository(dao)

	@Test
	fun `get - returns default when not set`() = runTest {
		val repo = createRepo()
		val result = repo.get(MAX_UPLOAD_SIZE)
		assertEquals(MAX_UPLOAD_SIZE.default, result)
	}

	@Test
	fun `set and get`() = runTest {
		val repo = createRepo()
		val newValue = 20
		repo.set(MAX_UPLOAD_SIZE, newValue)

		val result = repo.get(MAX_UPLOAD_SIZE)
		assertEquals(newValue, result)
	}

	@Test
	fun `get Boolean`() = runTest {
		val repo = createRepo()
		repo.set(MAINTENANCE_MODE, true)

		val result = repo.get(MAINTENANCE_MODE)
		assertTrue(result)

		repo.set(MAINTENANCE_MODE, false)
		assertFalse(repo.get(MAINTENANCE_MODE))
	}

	@Test
	fun `get and set String`() = runTest {
		val repo = createRepo()
		val newValue = "New Server Name"
		repo.set(SERVER_NAME, newValue)

		val result = repo.get(SERVER_NAME)
		assertEquals(newValue, result)
	}
}
