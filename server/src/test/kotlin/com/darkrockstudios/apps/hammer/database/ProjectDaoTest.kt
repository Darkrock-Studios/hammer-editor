package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProjectDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var projectDao: ProjectDao

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		projectDao = ProjectDao(db)

		setupKoin()
	}

	@Test
	fun `deleteProject - deletes its members and invites`() = runTest {
		val owner = accountDao.createAccount("dao-owner@example.com", "hash", "secret", isAdmin = false)
		val member = accountDao.createAccount("dao-member@example.com", "hash", "secret", isAdmin = false)
		val projectId = ProjectId.randomUUID()
		projectDao.createProject(owner, projectId, "Shared")
		val rowId = projectDao.getProjectId(owner, projectId)

		val members = db.serverDatabase.projectMemberQueries
		val invites = db.serverDatabase.projectInviteQueries
		members.insertMember(rowId, member)
		invites.insertInvite(rowId, "invitee@example.com", owner)

		projectDao.deleteProject(owner, projectId)

		assertNull(projectDao.getProjectIdOrNull(owner, projectId))
		assertFalse(members.isMember(rowId, member).executeAsOne())
		assertEquals(emptyList(), invites.getInvitesForProject(rowId).executeAsList())
	}
}
