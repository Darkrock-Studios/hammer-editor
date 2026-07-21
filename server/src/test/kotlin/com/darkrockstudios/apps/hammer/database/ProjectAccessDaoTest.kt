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
}
