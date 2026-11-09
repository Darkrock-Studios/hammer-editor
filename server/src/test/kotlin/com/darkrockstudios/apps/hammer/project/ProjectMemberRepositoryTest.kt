package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectInviteDao
import com.darkrockstudios.apps.hammer.database.ProjectMemberDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ProjectMemberRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var projectDao: ProjectDao
	private lateinit var sessions: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>
	private lateinit var repository: ProjectMemberRepository

	private val projectId = ProjectId("6f1c7a52-3f0e-4c8a-9a51-6b9f3f0d2c11")

	private var owner = 0L
	private var invitee = 0L

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		projectDao = ProjectDao(db)
		sessions = SyncSessionManager(Clock.System, SecureRandom())
		repository = ProjectMemberRepository(projectDao, ProjectMemberDao(db), ProjectInviteDao(db), accountDao, sessions)
	}

	private suspend fun seed() {
		owner = createAccount("owner@example.com", "Owner Pen")
		invitee = createAccount("invitee@example.com", "Invitee Pen")
		projectDao.createProject(OwnerId.unchecked(owner), projectId, "Shared Story")
	}

	private suspend fun createAccount(email: String, penName: String?): Long {
		val id = accountDao.createAccount(email, "hash", "secret-$email", isAdmin = false)
		if (penName != null) accountDao.updatePenName(id, penName)
		return id
	}

	private fun <T> SResult<T>.exception(): Throwable? = (this as ServerResult.Failure).exception

	private fun refusal(result: SResult<Unit>): InviteRefusal =
		assertIs<InviteRefusedException>(result.exception()).reason

	@Test
	fun `an accepted invite becomes a member with their pen name`() = runTest {
		seed()

		assertTrue(repository.createInvite(owner, projectId, " Invitee@Example.com ").isSuccess)
		val invite = repository.receivedInvites(invitee).single()
		assertEquals(projectId, invite.projectId)
		assertEquals("Shared Story", invite.projectName)
		assertEquals("Owner Pen", invite.ownerName)

		val accepted = repository.acceptInvite(invitee, invite.id)
		assertEquals(projectId, (accepted as ServerResult.Success).data)

		val members = (repository.listMembers(owner, projectId) as ServerResult.Success).data
		assertEquals(listOf(invitee to "Invitee Pen"), members.map { it.userId to it.penName })
		assertEquals(emptyList(), (repository.listInvites(owner, projectId) as ServerResult.Success).data)
		assertEquals(emptyList(), repository.receivedInvites(invitee))
	}

	@Test
	fun `the sixth person is refused, but re-inviting a pending address is not`() = runTest {
		seed()
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.acceptInvite(invitee, repository.receivedInvites(invitee).single().id)
		(1..4).forEach { assertTrue(repository.createInvite(owner, projectId, "pending$it@example.com").isSuccess) }

		assertEquals(InviteRefusal.LIMIT_REACHED, refusal(repository.createInvite(owner, projectId, "sixth@example.com")))
		assertTrue(repository.createInvite(owner, projectId, "pending1@example.com").isSuccess)
		assertEquals(4, (repository.listInvites(owner, projectId) as ServerResult.Success).data.size)
	}

	@Test
	fun `concurrent invites cannot pass the cap together`() = runTest {
		seed()

		withContext(Dispatchers.IO) {
			(1..8).map { async { repository.createInvite(owner, projectId, "racer$it@example.com") } }.awaitAll()
		}

		assertEquals(5, (repository.listInvites(owner, projectId) as ServerResult.Success).data.size)
	}

	@Test
	fun `invites to the owner, an existing member or a malformed address are refused`() = runTest {
		seed()
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.acceptInvite(invitee, repository.receivedInvites(invitee).single().id)

		assertEquals(InviteRefusal.OWN_ADDRESS, refusal(repository.createInvite(owner, projectId, "OWNER@example.com")))
		assertEquals(InviteRefusal.ALREADY_MEMBER, refusal(repository.createInvite(owner, projectId, "invitee@example.com")))
		assertEquals(InviteRefusal.INVALID_EMAIL, refusal(repository.createInvite(owner, projectId, "not-an-address")))
	}

	@Test
	fun `an owner without a pen name cannot invite`() = runTest {
		seed()
		accountDao.updatePenName(owner, null)

		assertEquals(
			InviteRefusal.OWNER_PEN_NAME_REQUIRED,
			refusal(repository.createInvite(owner, projectId, "invitee@example.com")),
		)
	}

	@Test
	fun `only the owner manages the project`() = runTest {
		seed()

		assertIs<NotOwnerException>(repository.createInvite(invitee, projectId, "x@example.com").exception())
		assertIs<NotOwnerException>(repository.listMembers(invitee, projectId).exception())
		assertIs<NotOwnerException>(repository.listInvites(invitee, projectId).exception())
		assertIs<NotOwnerException>(repository.removeMember(invitee, projectId, owner).exception())
		assertIs<NotOwnerException>(repository.revokeInvite(invitee, projectId, 1L).exception())
		val unknown = ProjectId("0b3c51a6-1e5d-4f3e-8d0b-7a2c9e4f6a10")
		assertIs<ProjectNotFound>(repository.listMembers(owner, unknown).exception())
	}

	@Test
	fun `accepting needs a pen name and an invite addressed to the account`() = runTest {
		seed()
		val stranger = createAccount("stranger@example.com", "Stranger Pen")
		val noPenName = createAccount("nopen@example.com", null)
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.createInvite(owner, projectId, "nopen@example.com")
		val inviteId = repository.receivedInvites(invitee).single().id

		assertIs<InviteNotFoundException>(repository.acceptInvite(stranger, inviteId).exception())
		assertIs<PenNameRequiredException>(
			repository.acceptInvite(noPenName, repository.receivedInvites(noPenName).single().id).exception()
		)
		assertEquals(2, (repository.listInvites(owner, projectId) as ServerResult.Success).data.size)
	}

	@Test
	fun `revoked and declined invites are gone`() = runTest {
		seed()
		createAccount("second@example.com", "Second Pen")
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.createInvite(owner, projectId, "second@example.com")
		val invites = (repository.listInvites(owner, projectId) as ServerResult.Success).data

		repository.revokeInvite(owner, projectId, invites.single { it.email == "invitee@example.com" }.id)
		val second = accountDao.findAccount("second@example.com")!!.id
		repository.declineInvite(second, repository.receivedInvites(second).single().id)

		assertEquals(emptyList(), (repository.listInvites(owner, projectId) as ServerResult.Success).data)
		assertIs<InviteNotFoundException>(repository.acceptInvite(invitee, invites.first().id).exception())
	}

	@Test
	fun `removing a member ends their project session`() = runTest {
		seed()
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.acceptInvite(invitee, repository.receivedInvites(invitee).single().id)
		val key = ProjectSyncKey(projectId)
		sessions.createNewSession(key) { _, syncId ->
			ProjectSynchronizationSession(invitee, ProjectDefinition("Shared Story", projectId), Clock.System.now(), syncId)
		}

		assertTrue(repository.removeMember(owner, projectId, invitee).isSuccess)

		assertEquals(emptyList(), (repository.listMembers(owner, projectId) as ServerResult.Success).data)
		assertNull(sessions.findSession(key))
	}

	@Test
	fun `a removed member's writing activity keeps its author and name`() = runTest {
		seed()
		repository.createInvite(owner, projectId, "invitee@example.com")
		repository.acceptInvite(invitee, repository.receivedInvites(invitee).single().id)
		val activity = ServerWritingActivityRepository(
			WritingActivityDao(db),
			projectDao,
			createJsonSerializer(),
			KtorSimpleLogger("ProjectMemberRepositoryTest"),
		)
		val ownerId = OwnerId.unchecked(owner)
		val projectDef = ProjectDefinition("Shared Story", projectId)
		activity.saveDeviceLog(ownerId, projectDef, invitee, "Invitee Pen", "device-a", DeviceLog("Laptop"))

		assertTrue(repository.removeMember(owner, projectId, invitee).isSuccess)

		val slot = (activity.loadAll(ownerId, projectDef) as ServerResult.Success).data.slots.single()
		assertEquals(invitee, slot.log.authorId)
		assertEquals("Invitee Pen", slot.log.authorName)
	}
}
