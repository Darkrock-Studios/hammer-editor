package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.AccountDeletionConfig
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AccountDeletionJobTest {

	@MockK
	private lateinit var accountDeletionService: AccountDeletionService

	private lateinit var clock: TestClock
	private val logger = LoggerFactory.getLogger(AccountDeletionJobTest::class.java)

	private fun account(id: Long, deletedAt: Instant) = Account(
		id = id,
		email = "user$id@example.com",
		pen_name = null,
		password_hash = "hash",
		cipher_secret = "secret",
		created = Clock.System.now() - 365.days,
		is_admin = false,
		last_sync = Clock.System.now(),
		bio = null,
		email_verified = true,
		community_member = false,
		deleted_at = deletedAt,
	)

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		clock = TestClock(Clock.System)
	}

	private fun createJob(retentionDays: Int = 30) = AccountDeletionJob(
		accountDeletionService = accountDeletionService,
		serverConfig = ServerConfig(accountDeletion = AccountDeletionConfig(retentionDays = retentionDays)),
		clock = clock,
		logger = logger,
	)

	@Test
	fun `tick - hard-deletes every account past the retention window`() = runTest {
		val cutoff = slot<Instant>()
		val due = listOf(
			account(1, clock.now() - 40.days),
			account(2, clock.now() - 31.days),
		)
		coEvery { accountDeletionService.findAccountsPastRetention(capture(cutoff)) } returns due

		createJob(retentionDays = 30).tick()

		assertEquals(clock.now() - 30.days, cutoff.captured)
		coVerify { accountDeletionService.hardDelete(1) }
		coVerify { accountDeletionService.hardDelete(2) }
	}

	@Test
	fun `tick - one failing account does not starve the rest`() = runTest {
		val due = listOf(
			account(1, clock.now() - 40.days),
			account(2, clock.now() - 31.days),
		)
		coEvery { accountDeletionService.findAccountsPastRetention(any()) } returns due
		coEvery { accountDeletionService.hardDelete(1) } throws IllegalStateException("boom")

		createJob().tick()

		coVerify { accountDeletionService.hardDelete(2) }
	}

	@Test
	fun `tick - nothing due is a no-op`() = runTest {
		coEvery { accountDeletionService.findAccountsPastRetention(any()) } returns emptyList()

		createJob().tick()

		coVerify(exactly = 0) { accountDeletionService.hardDelete(any()) }
	}
}
