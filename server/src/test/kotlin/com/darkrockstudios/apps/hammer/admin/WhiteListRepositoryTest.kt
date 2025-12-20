package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhiteListRepositoryTest : BaseTest() {

	private lateinit var whiteListDao: FakeWhiteListDao
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json

	@BeforeEach
	override fun setup() {
		super.setup()

		whiteListDao = FakeWhiteListDao()
		fileSystem = FakeFileSystem()
		json = createJsonSerializer()

		setupKoin()
	}

	private fun createRepo() = WhiteListRepository(whiteListDao, fileSystem, json)

	@Test
	fun `initialize - creates config if not exists`() = runTest {
		val repo = createRepo()
		repo.initialize()

		val configFile = getRootDataDirectory(fileSystem) / "whitelist_config.json"
		assertTrue(fileSystem.exists(configFile), "Config file should be created")
	}

	@Test
	fun `useWhiteList - returns true by default`() = runTest {
		val repo = createRepo()
		repo.initialize()

		assertTrue(repo.useWhiteList(), "Should be enabled by default")
	}

	@Test
	fun `setWhiteListEnabled - updates config`() = runTest {
		val repo = createRepo()
		repo.initialize()

		repo.setWhiteListEnabled(false)
		assertFalse(repo.useWhiteList(), "Should be disabled after setting to false")

		repo.setWhiteListEnabled(true)
		assertTrue(repo.useWhiteList(), "Should be enabled after setting to true")
	}

	@Test
	fun `setWhiteListEnabled - persists`() = runTest {
		val repo1 = createRepo()
		repo1.initialize()
		repo1.setWhiteListEnabled(false)

		val repo2 = createRepo()
		assertFalse(repo2.useWhiteList(), "Should be disabled in new repo instance")
	}

	@Test
	fun `getWhiteList - all`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it) }

		val repo = createRepo()
		val result = repo.getWhiteList()

		assertEquals(emails.sorted(), result)
	}

	@Test
	fun `getWhiteList - paginated`() = runTest {
		val emails = (1..25).map { "user$it@example.com" }.sorted()
		emails.forEach { whiteListDao.addToWhiteList(it) }

		val repo = createRepo()
		val result = repo.getWhiteList(page = 1, pageSize = 10)

		assertEquals(emails.drop(10).take(10), result)
	}

	@Test
	fun `getWhiteListCount`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it) }

		val repo = createRepo()
		val result = repo.getWhiteListCount()

		assertEquals(2L, result)
	}

	@Test
	fun `isOnWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com")

		val repo = createRepo()
		val result = repo.isOnWhiteList("  TEST@Example.com  ")

		assertTrue(result)
	}

	@Test
	fun `addToWhiteList - cleans email`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("  TEST@Example.com  ")

		assertTrue(whiteListDao.isWhiteListed("test@example.com"))
	}

	@Test
	fun `removeFromWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com")

		val repo = createRepo()
		repo.removeFromWhiteList("  TEST@Example.com  ")

		assertFalse(whiteListDao.isWhiteListed("test@example.com"))
	}
}
