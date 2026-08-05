package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.UserDataPurgeDao
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AccountDeletionServiceTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var penNameService: PenNameService

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK
	private lateinit var userDataPurgeDao: UserDataPurgeDao

	@MockK
	private lateinit var projectsSyncManager: SyncSessionManager<Long, ProjectsSynchronizationSession>

	@MockK
	private lateinit var projectSyncManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	private lateinit var clock: TestClock
	private lateinit var service: AccountDeletionService

	private val userId = 1L
	private val email = "writer@example.com"

	private fun account(
		isAdmin: Boolean = false,
		deletedAt: kotlin.time.Instant? = null,
	) = Account(
		id = userId,
		email = email,
		pen_name = "Ada Lovelace",
		password_hash = "hash",
		cipher_secret = "secret",
		created = Clock.System.now() - 128.days,
		is_admin = isAdmin,
		last_sync = Clock.System.now(),
		bio = null,
		email_verified = true,
		community_member = true,
		deleted_at = deletedAt,
	)

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		clock = TestClock(Clock.System)
		service = AccountDeletionService(
			accountsRepository = accountsRepository,
			penNameService = penNameService,
			whiteListRepository = whiteListRepository,
			userDataPurgeDao = userDataPurgeDao,
			projectsSyncManager = projectsSyncManager,
			projectSyncManager = projectSyncManager,
			clock = clock,
		)

		coEvery { accountsRepository.forceLogout(any()) } returns true
		every { projectsSyncManager.terminateSession(any()) } returns true
	}

	@Test
	fun `softDelete - locks the account out then releases its public presence`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account() andThen account(deletedAt = clock.now())

		val result = service.softDelete(userId)

		assertTrue(isSuccess(result))
		coVerifyOrder {
			accountsRepository.markDeleted(userId, clock.now())
			accountsRepository.forceLogout(email)
			projectsSyncManager.terminateSession(userId)
			projectSyncManager.terminateSessions(any())
			penNameService.releasePenName(userId)
			accountsRepository.updateCommunityMember(userId, false)
		}
	}

	@Test
	fun `softDelete - missing account fails`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns null

		val result = service.softDelete(userId)

		assertTrue(result.isFailure)
		coVerify(exactly = 0) { accountsRepository.markDeleted(any(), any()) }
	}

	@Test
	fun `softDelete - retry on an already-deleted account re-runs the idempotent cleanup`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account(deletedAt = clock.now() - 1.days)

		val result = service.softDelete(userId)

		assertTrue(isSuccess(result))
		coVerify { penNameService.releasePenName(userId) }
		coVerify { accountsRepository.forceLogout(email) }
	}

	@Test
	fun `softDelete - aborts before destructive steps when the flag did not land`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account() andThen account(deletedAt = null)

		val result = service.softDelete(userId)

		assertTrue(result.isFailure)
		coVerify(exactly = 0) { accountsRepository.forceLogout(any()) }
		coVerify(exactly = 0) { penNameService.releasePenName(any()) }
	}

	@Test
	fun `softDelete - admin accounts can never be deleted`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns account(isAdmin = true)

		val result = service.softDelete(userId)

		assertTrue(result.isFailure)
		coVerify(exactly = 0) { accountsRepository.markDeleted(any(), any()) }
		coVerify(exactly = 0) { penNameService.releasePenName(any()) }
	}

	@Test
	fun `restore - un-flags an existing account`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account(deletedAt = clock.now() - 1.days)

		assertTrue(service.restore(userId))
		coVerify { accountsRepository.restoreDeleted(userId) }
	}

	@Test
	fun `restore - missing account returns false`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns null

		assertFalse(service.restore(userId))
		coVerify(exactly = 0) { accountsRepository.restoreDeleted(any()) }
	}

	@Test
	fun `hardDelete - purges user data and removes the whitelist entry`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account(deletedAt = clock.now() - 31.days)
		coEvery { userDataPurgeDao.purgeUserData(userId) } returns true

		service.hardDelete(userId)

		coVerify { userDataPurgeDao.purgeUserData(userId) }
		coVerify { whiteListRepository.removeFromWhiteList(email) }
	}

	@Test
	fun `hardDelete - missing account is a no-op`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns null

		service.hardDelete(userId)

		coVerify(exactly = 0) { userDataPurgeDao.purgeUserData(any()) }
		coVerify(exactly = 0) { whiteListRepository.removeFromWhiteList(any()) }
	}

	@Test
	fun `hardDelete - account restored since the job snapshot is left alone`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns account(deletedAt = null)

		service.hardDelete(userId)

		coVerify(exactly = 0) { userDataPurgeDao.purgeUserData(any()) }
		coVerify(exactly = 0) { whiteListRepository.removeFromWhiteList(any()) }
	}

	@Test
	fun `hardDelete - whitelist entry kept when the purge transaction refuses`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(userId) } returns
			account(deletedAt = clock.now() - 31.days)
		coEvery { userDataPurgeDao.purgeUserData(userId) } returns false

		service.hardDelete(userId)

		coVerify(exactly = 0) { whiteListRepository.removeFromWhiteList(any()) }
	}
}
