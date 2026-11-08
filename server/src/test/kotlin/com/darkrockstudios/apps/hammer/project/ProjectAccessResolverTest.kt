package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectMemberDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class ProjectAccessResolverTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var projectDao: ProjectDao
	private lateinit var whiteListRepository: WhiteListRepository
	private lateinit var resolver: ProjectAccessResolver

	private val projectId = ProjectId("6f1c7a52-3f0e-4c8a-9a51-6b9f3f0d2c11")
	private val projectDef = ProjectDefinition("Shared Story", projectId)

	private var owner = 0L
	private var member = 0L
	private var stranger = 0L

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		projectDao = ProjectDao(db)
		whiteListRepository = WhiteListRepository(WhiteListDao(db), Clock.System)
		resolver = ProjectAccessResolver(projectDao, ProjectMemberDao(db), accountDao, whiteListRepository)
	}

	private suspend fun seed() {
		owner = createWhitelisted("owner@example.com")
		member = createWhitelisted("member@example.com")
		stranger = createWhitelisted("stranger@example.com")
		projectDao.createProject(owner, projectId, projectDef.name)
		val rowId = projectDao.getProjectId(owner, projectId)
		db.serverDatabase.projectMemberQueries.insertMember(rowId, member)
	}

	private suspend fun createWhitelisted(email: String): Long {
		whiteListRepository.addToWhiteList(email)
		return accountDao.createAccount(email, "hash", "secret-$email", isAdmin = false)
	}

	private fun granted(isOwner: Boolean) = ProjectAccessResult.Granted(
		ProjectAccess(projectDef, OwnerId.ofCaller(owner), isOwner = isOwner)
	)

	@Test
	fun `owner is granted as owner`() = runTest {
		seed()
		assertEquals(granted(isOwner = true), resolver.resolve(owner, projectId))
	}

	@Test
	fun `member is granted with the owner as storage owner`() = runTest {
		seed()
		assertEquals(granted(isOwner = false), resolver.resolve(member, projectId))
	}

	@Test
	fun `non-member is refused`() = runTest {
		seed()
		assertEquals(ProjectAccessResult.NotMember, resolver.resolve(stranger, projectId))
	}

	@Test
	fun `unknown id is unknown`() = runTest {
		seed()
		val unknown = ProjectId("0b3c51a6-1e5d-4f3e-8d0b-7a2c9e4f6a10")
		assertEquals(ProjectAccessResult.Unknown, resolver.resolve(owner, unknown))
	}

	@Test
	fun `member is refused while the owner is soft-deleted`() = runTest {
		seed()
		accountDao.markDeleted(owner, Clock.System.now())
		assertEquals(ProjectAccessResult.NotMember, resolver.resolve(member, projectId))
	}

	@Test
	fun `member is refused once the owner leaves the allowed list`() = runTest {
		seed()
		db.serverDatabase.whiteListQueries.removeFromWhiteList("owner@example.com")
		assertEquals(ProjectAccessResult.NotMember, resolver.resolve(member, projectId))
	}

	@Test
	fun `member of an admin's project is granted though the admin is not on the allowed list`() = runTest {
		owner = accountDao.createAccount("admin-owner@example.com", "hash", "secret-admin", isAdmin = true)
		member = createWhitelisted("member@example.com")
		projectDao.createProject(owner, projectId, projectDef.name)
		db.serverDatabase.projectMemberQueries.insertMember(projectDao.getProjectId(owner, projectId), member)

		assertEquals(granted(isOwner = false), resolver.resolve(member, projectId))
	}
}
