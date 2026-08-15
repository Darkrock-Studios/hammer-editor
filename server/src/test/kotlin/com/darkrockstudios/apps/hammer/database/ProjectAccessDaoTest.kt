package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectAccessDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var dao: ProjectAccessDao

	private val attackerProjectId = 100L
	private val victimProjectId = 200L

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		dao = ProjectAccessDao(db)
		setupKoin()
	}

	@Test
	fun `deleteAccessById does not delete an access row belonging to another project`() = runTest {
		dao.insertAccess(victimProjectId, "victim-secret", null)
		val victimAccessId = dao.getAllAccessForProject(victimProjectId).single().id

		val deleted = dao.deleteAccessById(victimAccessId, attackerProjectId)

		assertFalse(deleted, "A foreign project must not be able to delete this access row")
		assertEquals(1, dao.getAllAccessForProject(victimProjectId).size)
	}

	@Test
	fun `deleteAccessById deletes the access row of its owning project`() = runTest {
		dao.insertAccess(victimProjectId, "victim-secret", null)
		val accessId = dao.getAllAccessForProject(victimProjectId).single().id

		val deleted = dao.deleteAccessById(accessId, victimProjectId)

		assertTrue(deleted)
		assertEquals(0, dao.getAllAccessForProject(victimProjectId).size)
	}

	@Test
	fun `insertAccessWithScenes persists the access row and its scene ids`() = runTest {
		val accessId = dao.insertAccessWithScenes(victimProjectId, "secret", null, listOf(3, 7, 12))

		assertEquals(accessId, dao.getAllAccessForProject(victimProjectId).single().id)
		assertEquals(listOf(3, 7, 12), dao.getSceneIdsForAccess(accessId).sorted())
	}

	@Test
	fun `insertAccessWithScenes with no scenes creates an unrestricted share`() = runTest {
		val accessId = dao.insertAccessWithScenes(victimProjectId, "secret", null, emptyList())

		assertEquals(emptyList(), dao.getSceneIdsForAccess(accessId))
	}

	@Test
	fun `sceneCountsForAccessIds counts only limited shares`() = runTest {
		val limitedId = dao.insertAccessWithScenes(victimProjectId, "limited", null, listOf(1, 2, 3))
		val unrestrictedId = dao.insertAccessWithScenes(victimProjectId, "unrestricted", null, emptyList())

		val counts = dao.sceneCountsForAccessIds(listOf(limitedId, unrestrictedId))

		assertEquals(mapOf(limitedId to 3), counts)
	}

	@Test
	fun `deleteAccessById removes the scene rows of its share`() = runTest {
		val accessId = dao.insertAccessWithScenes(victimProjectId, "secret", null, listOf(1, 2))

		val deleted = dao.deleteAccessById(accessId, victimProjectId)

		assertTrue(deleted)
		assertEquals(emptyList(), dao.getSceneIdsForAccess(accessId))
	}

	@Test
	fun `deleteAccessById from a foreign project leaves scene rows intact`() = runTest {
		val accessId = dao.insertAccessWithScenes(victimProjectId, "secret", null, listOf(1, 2))

		val deleted = dao.deleteAccessById(accessId, attackerProjectId)

		assertFalse(deleted)
		assertEquals(listOf(1, 2), dao.getSceneIdsForAccess(accessId).sorted())
	}
}
