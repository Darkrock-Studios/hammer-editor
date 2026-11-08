package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
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

class UserDataPurgeDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var purgeDao: UserDataPurgeDao

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		purgeDao = UserDataPurgeDao(db)

		setupKoin()
	}

	@Test
	fun `purgeUserData - deletes a soft-deleted account`() = runTest {
		val userId = accountDao.createAccount("purge-me@example.com", "hash", "secret", isAdmin = false)
		accountDao.markDeleted(userId, Clock.System.now())

		assertTrue(purgeDao.purgeUserData(userId))
		assertNull(accountDao.getAccount(userId))
	}

	@Test
	fun `purgeUserData - deletes the account's project groups`() = runTest {
		val userId = accountDao.createAccount("purge-groups@example.com", "hash", "secret", isAdmin = false)
		val groupDao = ProjectGroupDao(db)
		groupDao.createGroup(userId, GroupId.randomUUID(), "Trilogy", null, null)
		accountDao.markDeleted(userId, Clock.System.now())

		assertTrue(purgeDao.purgeUserData(userId))
		assertEquals(emptyList(), groupDao.getGroups(userId))
	}

	@Test
	fun `purgeUserData - deletes memberships and invites on both sides`() = runTest {
		val owner = accountDao.createAccount("purge-owner@example.com", "hash", "secret", isAdmin = false)
		val other = accountDao.createAccount("purge-other@example.com", "hash", "secret", isAdmin = false)
		val projectDao = ProjectDao(db)
		val ownedId = ProjectId.randomUUID()
		val otherId = ProjectId.randomUUID()
		projectDao.createProject(owner, ownedId, "Owned")
		projectDao.createProject(other, otherId, "Other's")
		val ownedRow = projectDao.getProjectId(owner, ownedId)
		val otherRow = projectDao.getProjectId(other, otherId)

		val members = db.serverDatabase.projectMemberQueries
		val invites = db.serverDatabase.projectInviteQueries
		members.insertMember(ownedRow, other)
		members.insertMember(otherRow, owner)
		invites.insertInvite(ownedRow, "someone@example.com", owner)
		invites.insertInvite(otherRow, "someone-else@example.com", owner)
		invites.insertInvite(otherRow, "Purge-Owner@example.com", other)
		accountDao.markDeleted(owner, Clock.System.now())

		assertTrue(purgeDao.purgeUserData(owner))
		assertFalse(members.isMember(ownedRow, other).executeAsOne())
		assertFalse(members.isMember(otherRow, owner).executeAsOne())
		assertEquals(emptyList(), invites.getInvitesForProject(ownedRow).executeAsList())
		assertEquals(emptyList(), invites.getInvitesForProject(otherRow).executeAsList())
	}

	@Test
	fun `purgeUserData - refuses an account that is not soft-deleted`() = runTest {
		val userId = accountDao.createAccount("keep-me@example.com", "hash", "secret", isAdmin = false)

		assertFalse(purgeDao.purgeUserData(userId), "A restored account must never be purged")
		assertNotNull(accountDao.getAccount(userId))
	}

	@Test
	fun `purgeUserData - missing account returns false`() = runTest {
		assertFalse(purgeDao.purgeUserData(999_999L))
	}
}
