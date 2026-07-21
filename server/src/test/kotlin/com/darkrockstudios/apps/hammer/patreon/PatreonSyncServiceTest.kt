package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PatreonSyncServiceTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var configDao: ServerConfigDao
	private lateinit var configRepository: ConfigRepository
	private lateinit var whiteListRepository: WhiteListRepository
	private lateinit var clock: TestClock
	private lateinit var patreonApiClient: PatreonApiClient
	private lateinit var accountsRepository: AccountsRepository
	private val logger = LoggerFactory.getLogger(PatreonSyncServiceTest::class.java)

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		whiteListDao = WhiteListDao(db)
		configDao = ServerConfigDao(db)
		configRepository = ConfigRepository(configDao)
		clock = TestClock(Clock.System)
		whiteListRepository = WhiteListRepository(whiteListDao, configRepository, clock)
		patreonApiClient = mockk()
		accountsRepository = mockk()

		setupKoin()
	}

	private fun createSyncService() = PatreonSyncService(
		patreonApiClient = patreonApiClient,
		whiteListRepository = whiteListRepository,
		accountsRepository = accountsRepository,
		configRepository = configRepository,
		clock = clock,
		logger = logger
	)

	private suspend fun enablePatreon(
		campaignId: String = "test-campaign",
		accessToken: String = "test-token",
		minimumAmountCents: Int = 500
	) {
		val config = PatreonConfig(
			enabled = true,
			campaignId = campaignId,
			creatorAccessToken = accessToken,
			minimumAmountCents = minimumAmountCents
		)
		configRepository.set(AdminServerConfig.PATREON_CONFIG, config)
	}

	@Test
	fun `sync skipped when disabled`() = runTest {
		// Patreon disabled by default
		val service = createSyncService()

		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(0, result.getOrThrow().added)
		assertEquals(0, result.getOrThrow().removed)
	}

	@Test
	fun `sync fails when missing campaign ID`() = runTest {
		val config = PatreonConfig(enabled = true, campaignId = "", creatorAccessToken = "token")
		configRepository.set(AdminServerConfig.PATREON_CONFIG, config)

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isFailure)
		assertIs<PatreonSyncException>(result.exceptionOrNull())
	}

	@Test
	fun `new patron joins - added to whitelist`() = runTest {
		enablePatreon()

		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(listOf("patron@example.com"))

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(1, result.getOrThrow().added)
		assertTrue(whiteListRepository.isOnWhiteList("patron@example.com"))

		// Verify reason is "Patreon"
		val entries = whiteListRepository.getWhiteListByReason(PatreonSyncService.WHITELIST_REASON)
		assertEquals(1, entries.size)
		assertEquals("patron@example.com", entries[0].email)
	}

	@Test
	fun `existing patron with different reason - not modified`() = runTest {
		// Add user to whitelist with different reason
		whiteListRepository.addToWhiteList("betatester@example.com", "Beta tester")

		enablePatreon()

		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(listOf("betatester@example.com"))

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(0, result.getOrThrow().added)
		assertEquals(1, result.getOrThrow().skipped)

		// Verify reason is still "Beta tester"
		val entries = whiteListRepository.getWhiteListByReason("Beta tester")
		assertEquals(1, entries.size)
		assertEquals("betatester@example.com", entries[0].email)

		// Verify no Patreon entries
		val patreonEntries = whiteListRepository.getWhiteListByReason(PatreonSyncService.WHITELIST_REASON)
		assertEquals(0, patreonEntries.size)
	}

	@Test
	fun `patron unsubscribes - removed from whitelist and logged out`() = runTest {
		// Add user as Patreon member
		whiteListRepository.addToWhiteList("lapsed@example.com", PatreonSyncService.WHITELIST_REASON)

		enablePatreon()

		// Patron is no longer in the API response
		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(emptyList())

		coEvery { accountsRepository.forceLogout("lapsed@example.com") } returns true

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(1, result.getOrThrow().removed)
		assertFalse(whiteListRepository.isOnWhiteList("lapsed@example.com"))

		// Verify force logout was called
		coVerify { accountsRepository.forceLogout("lapsed@example.com") }
	}

	@Test
	fun `non-Patreon user unaffected when not in API`() = runTest {
		// Add user with non-Patreon reason
		whiteListRepository.addToWhiteList("friend@example.com", "Friend")

		enablePatreon()

		// Friend is not in Patreon API (they're not a patron)
		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(emptyList())

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(0, result.getOrThrow().removed)

		// Friend should still be on whitelist
		assertTrue(whiteListRepository.isOnWhiteList("friend@example.com"))
	}

	@Test
	fun `sync with no changes`() = runTest {
		// Add existing Patreon member
		whiteListRepository.addToWhiteList("patron@example.com", PatreonSyncService.WHITELIST_REASON)

		enablePatreon()

		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(listOf("patron@example.com"))

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(0, result.getOrThrow().added)
		assertEquals(0, result.getOrThrow().removed)
	}

	@Test
	fun `force logout works`() = runTest {
		coEvery { accountsRepository.forceLogout("user@example.com") } returns true

		val service = createSyncService()
		service.forceLogout("user@example.com")

		coVerify { accountsRepository.forceLogout("user@example.com") }
	}

	@Test
	fun `force logout handles missing account gracefully`() = runTest {
		coEvery { accountsRepository.forceLogout("nonexistent@example.com") } returns false

		val service = createSyncService()
		// Should not throw
		service.forceLogout("nonexistent@example.com")
	}

	@Test
	fun `email normalization works`() = runTest {
		enablePatreon()

		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(listOf("  PATRON@EXAMPLE.COM  "))

		val service = createSyncService()
		val result = service.performFullSync()

		assertTrue(result.isSuccess)
		assertEquals(1, result.getOrThrow().added)

		// Should be normalized to lowercase
		assertTrue(whiteListRepository.isOnWhiteList("patron@example.com"))
	}

	@Test
	fun `last sync time is updated`() = runTest {
		enablePatreon()

		coEvery {
			patreonApiClient.fetchAllQualifyingMembers("test-campaign", "test-token", 500)
		} returns Result.success(emptyList())

		val service = createSyncService()
		service.performFullSync()

		val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		assertEquals(clock.now().toString(), config.lastSync)
	}

	@Test
	fun `patron count returns correct value`() = runTest {
		whiteListRepository.addToWhiteList("patron1@example.com", PatreonSyncService.WHITELIST_REASON)
		whiteListRepository.addToWhiteList("patron2@example.com", PatreonSyncService.WHITELIST_REASON)
		whiteListRepository.addToWhiteList("friend@example.com", "Friend")

		val service = createSyncService()
		val count = service.getPatreonWhitelistCount()

		assertEquals(2L, count)
	}

	@Test
	fun `patron with accounts count returns correct value`() = runTest {
		// Add patrons to whitelist
		whiteListRepository.addToWhiteList("patron1@example.com", PatreonSyncService.WHITELIST_REASON)
		whiteListRepository.addToWhiteList("patron2@example.com", PatreonSyncService.WHITELIST_REASON)
		whiteListRepository.addToWhiteList("patron3@example.com", PatreonSyncService.WHITELIST_REASON)
		whiteListRepository.addToWhiteList("friend@example.com", "Friend")

		// Create accounts for only some of the patrons
		db.serverDatabase.accountQueries.testInsertAccount(
			email = "patron1@example.com",
			password_hash = "hash",
			cipher_secret = "secret",
			is_admin = false,
			created = Instant.parse("2024-01-01T00:00:00Z"),
			last_sync = Instant.parse("2024-01-01T00:00:00Z")
		)
		db.serverDatabase.accountQueries.testInsertAccount(
			email = "patron3@example.com",
			password_hash = "hash",
			cipher_secret = "secret",
			is_admin = false,
			created = Instant.parse("2024-01-01T00:00:00Z"),
			last_sync = Instant.parse("2024-01-01T00:00:00Z")
		)

		val service = createSyncService()
		val count = service.getPatreonMembersWithAccountsCount()

		// Only patron1 and patron3 have accounts (patron2 doesn't, friend is not a Patreon member)
		assertEquals(2L, count)
	}
}
