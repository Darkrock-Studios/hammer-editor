package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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

	private suspend fun insertShare(
		projectId: Long,
		password: String,
		sceneIds: Collection<Int> = emptyList(),
	): Long? = dao.insertAccessWithScenes(projectId, password, null, sceneIds, Clock.System.now())

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
		val accessId = assertNotNull(insertShare(victimProjectId, "secret", listOf(3, 7, 12)))

		assertEquals(accessId, dao.getAllAccessForProject(victimProjectId).single().id)
		assertEquals(listOf(3, 7, 12), dao.getSceneIdsForAccess(accessId).sorted())
	}

	@Test
	fun `insertAccessWithScenes with no scenes creates an unrestricted share`() = runTest {
		val accessId = assertNotNull(insertShare(victimProjectId, "secret"))

		assertEquals(emptyList(), dao.getSceneIdsForAccess(accessId))
	}

	@Test
	fun `insertAccessWithScenes refuses a password already used by a live share`() = runTest {
		assertNotNull(insertShare(victimProjectId, "secret"))

		val second = insertShare(victimProjectId, "secret", listOf(1))

		assertNull(second, "a live duplicate must not insert")
		assertEquals(1, dao.getAllAccessForProject(victimProjectId).size)
	}

	@Test
	fun `insertAccessWithScenes allows reusing the password of an expired share`() = runTest {
		// Raw insert: the expires-after-published CHECK bars seeding an already-expired
		// row through the queries.
		db.executeAsync(
			"INSERT INTO project_access(project_id, access_password, published_at, expires_at) " +
				"VALUES ($victimProjectId, 'secret', '2019-01-01T00:00:00Z', '2020-01-01T00:00:00Z')"
		)

		val accessId = insertShare(victimProjectId, "secret", listOf(1))

		assertNotNull(accessId, "an expired share must not block its password")
	}

	@Test
	fun `insertAccessWithScenes allows the same password on a different project`() = runTest {
		assertNotNull(insertShare(victimProjectId, "secret"))

		assertNotNull(insertShare(attackerProjectId, "secret"))
	}

	@Test
	fun `getSceneIdsForAccessIds maps only limited shares`() = runTest {
		val limitedId = assertNotNull(insertShare(victimProjectId, "limited", listOf(1, 2, 3)))
		val unrestrictedId = assertNotNull(insertShare(victimProjectId, "unrestricted"))

		val idsByAccess = dao.getSceneIdsForAccessIds(listOf(limitedId, unrestrictedId))

		assertEquals(mapOf(limitedId to setOf(1, 2, 3)), idsByAccess)
	}

	@Test
	fun `deleteAccessById removes the scene rows of its share`() = runTest {
		val accessId = assertNotNull(insertShare(victimProjectId, "secret", listOf(1, 2)))

		val deleted = dao.deleteAccessById(accessId, victimProjectId)

		assertTrue(deleted)
		assertEquals(emptyList(), dao.getSceneIdsForAccess(accessId))
	}

	@Test
	fun `deleteAccessById from a foreign project leaves scene rows intact`() = runTest {
		val accessId = assertNotNull(insertShare(victimProjectId, "secret", listOf(1, 2)))

		val deleted = dao.deleteAccessById(accessId, attackerProjectId)

		assertFalse(deleted)
		assertEquals(listOf(1, 2), dao.getSceneIdsForAccess(accessId).sorted())
	}

	@Test
	fun `deleteAccess removes every share and scene row of the project`() = runTest {
		val accessId = assertNotNull(insertShare(victimProjectId, "secret", listOf(1, 2)))

		dao.deleteAccess(victimProjectId)

		assertEquals(0, dao.getAllAccessForProject(victimProjectId).size)
		// FK enforcement is off in this harness, so orphans would survive a cascade-only delete.
		assertEquals(emptyList(), dao.getSceneIdsForAccess(accessId))
	}
}
