package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatreonWebhookHandlerTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var configDao: ServerConfigDao
	private lateinit var configRepository: ConfigRepository
	private lateinit var patreonApiClient: PatreonApiClient
	private lateinit var patreonSyncService: PatreonSyncService
	private val logger = LoggerFactory.getLogger(PatreonWebhookHandlerTest::class.java)

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		configDao = ServerConfigDao(db)
		configRepository = ConfigRepository(configDao)
		patreonApiClient = mockk()
		patreonSyncService = mockk()

		setupKoin()
	}

	private fun createHandler() = PatreonWebhookHandler(
		patreonApiClient = patreonApiClient,
		patreonSyncService = patreonSyncService,
		configRepository = configRepository,
		logger = logger
	)

	private suspend fun enablePatreon(
		webhookSecret: String = "test-secret"
	) {
		val config = PatreonConfig(
			enabled = true,
			campaignId = "test-campaign",
			creatorAccessToken = "test-token",
			webhookSecret = webhookSecret,
			minimumAmountCents = 500
		)
		configRepository.set(AdminServerConfig.PATREON_CONFIG, config)
	}

	private fun computeSignature(payload: String, secret: String): String {
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(payload.toByteArray())
		return hash.joinToString("") { "%02x".format(it) }
	}

	@Test
	fun `returns Disabled when Patreon integration is disabled`() = runTest {
		// Patreon disabled by default
		val handler = createHandler()

		val result = handler.handleWebhook(
			payload = """{"data":{}}""",
			signature = "some-signature",
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.Disabled)
	}

	@Test
	fun `returns MissingSignature when signature header is null`() = runTest {
		enablePatreon()
		val handler = createHandler()

		val result = handler.handleWebhook(
			payload = """{"data":{}}""",
			signature = null,
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.MissingSignature)
	}

	@Test
	fun `returns MissingSignature when signature header is blank`() = runTest {
		enablePatreon()
		val handler = createHandler()

		val result = handler.handleWebhook(
			payload = """{"data":{}}""",
			signature = "   ",
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.MissingSignature)
	}

	@Test
	fun `returns NotConfigured when webhook secret is not set`() = runTest {
		enablePatreon(webhookSecret = "")
		val handler = createHandler()

		val result = handler.handleWebhook(
			payload = """{"data":{}}""",
			signature = "some-signature",
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.NotConfigured)
	}

	@Test
	fun `returns InvalidSignature when signature verification fails`() = runTest {
		enablePatreon()
		val handler = createHandler()

		every {
			patreonApiClient.verifyWebhookSignature(any(), any(), any())
		} returns false

		val result = handler.handleWebhook(
			payload = """{"data":{}}""",
			signature = "invalid-signature",
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.InvalidSignature)
	}

	@Test
	fun `triggers full sync on valid webhook`() = runTest {
		enablePatreon()
		val handler = createHandler()
		val payload = """{"data":{"id":"123"}}"""
		val signature = computeSignature(payload, "test-secret")

		every {
			patreonApiClient.verifyWebhookSignature(payload, signature, "test-secret")
		} returns true

		coEvery {
			patreonSyncService.performFullSync()
		} returns Result.success(SyncResult(added = 1, removed = 0, skipped = 0))

		val result = handler.handleWebhook(
			payload = payload,
			signature = signature,
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.Success)
		assertEquals(1, (result as WebhookResult.Success).syncResult.added)

		coVerify { patreonSyncService.performFullSync() }
	}

	@Test
	fun `returns SyncFailed when sync fails`() = runTest {
		enablePatreon()
		val handler = createHandler()
		val payload = """{"data":{"id":"123"}}"""
		val signature = computeSignature(payload, "test-secret")

		every {
			patreonApiClient.verifyWebhookSignature(payload, signature, "test-secret")
		} returns true

		coEvery {
			patreonSyncService.performFullSync()
		} returns Result.failure(RuntimeException("API error"))

		val result = handler.handleWebhook(
			payload = payload,
			signature = signature,
			eventType = "members:pledge:create"
		)

		assertTrue(result is WebhookResult.SyncFailed)
		assertEquals("API error", (result as WebhookResult.SyncFailed).error)
	}

	@Test
	fun `handles members create event`() = runTest {
		enablePatreon()
		val handler = createHandler()
		val payload = """{"data":{"id":"123","type":"member"}}"""
		val signature = computeSignature(payload, "test-secret")

		every {
			patreonApiClient.verifyWebhookSignature(payload, signature, "test-secret")
		} returns true

		coEvery {
			patreonSyncService.performFullSync()
		} returns Result.success(SyncResult(added = 1, removed = 0, skipped = 0))

		val result = handler.handleWebhook(
			payload = payload,
			signature = signature,
			eventType = "members:create"
		)

		assertTrue(result is WebhookResult.Success)
		coVerify { patreonSyncService.performFullSync() }
	}

	@Test
	fun `handles members delete event`() = runTest {
		enablePatreon()
		val handler = createHandler()
		val payload = """{"data":{"id":"123","type":"member"}}"""
		val signature = computeSignature(payload, "test-secret")

		every {
			patreonApiClient.verifyWebhookSignature(payload, signature, "test-secret")
		} returns true

		coEvery {
			patreonSyncService.performFullSync()
		} returns Result.success(SyncResult(added = 0, removed = 1, skipped = 0))

		val result = handler.handleWebhook(
			payload = payload,
			signature = signature,
			eventType = "members:delete"
		)

		assertTrue(result is WebhookResult.Success)
		assertEquals(1, (result as WebhookResult.Success).syncResult.removed)
		coVerify { patreonSyncService.performFullSync() }
	}

	@Test
	fun `handles members pledge update event`() = runTest {
		enablePatreon()
		val handler = createHandler()
		val payload = """{"data":{"id":"123","type":"member"}}"""
		val signature = computeSignature(payload, "test-secret")

		every {
			patreonApiClient.verifyWebhookSignature(payload, signature, "test-secret")
		} returns true

		coEvery {
			patreonSyncService.performFullSync()
		} returns Result.success(SyncResult(added = 0, removed = 0, skipped = 1))

		val result = handler.handleWebhook(
			payload = payload,
			signature = signature,
			eventType = "members:pledge:update"
		)

		assertTrue(result is WebhookResult.Success)
		coVerify { patreonSyncService.performFullSync() }
	}
}
