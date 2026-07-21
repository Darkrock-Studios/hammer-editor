package com.darkrockstudios.apps.hammer.email

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostmarkEmailServiceTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var dao: ServerConfigDao
	private lateinit var configRepository: ConfigRepository

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		dao = ServerConfigDao(db)
		configRepository = ConfigRepository(dao)

		setupKoin()
	}

	private fun createService() = PostmarkEmailService(configRepository)

	@Test
	fun `isConfigured - returns false when server token is empty`() = runTest {
		val config = PostmarkConfig(
			serverToken = "",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false when from address is empty`() = runTest {
		val config = PostmarkConfig(
			serverToken = "test-server-token",
			fromAddress = "",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false when both are empty`() = runTest {
		val config = PostmarkConfig(
			serverToken = "",
			fromAddress = "",
			fromName = ""
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns true when server token and from address are set`() = runTest {
		val config = PostmarkConfig(
			serverToken = "test-server-token",
			fromAddress = "test@example.com",
			fromName = "Test Server"
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		assertTrue(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns true even with empty from name`() = runTest {
		val config = PostmarkConfig(
			serverToken = "test-server-token",
			fromAddress = "test@example.com",
			fromName = ""
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		assertTrue(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false with default config`() = runTest {
		// Default config has empty server token and from address
		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `sendEmail - returns failure when server token not configured`() = runTest {
		val config = PostmarkConfig(
			serverToken = "",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		val result = service.sendEmail(
			to = "recipient@example.com",
			subject = "Test Subject",
			bodyHtml = "<p>Test body</p>"
		)

		assertTrue(result is EmailResult.Failure)
		assertTrue((result as EmailResult.Failure).reason.contains("server token"))
	}

	@Test
	fun `sendEmail - returns failure when from address not configured`() = runTest {
		val config = PostmarkConfig(
			serverToken = "test-server-token",
			fromAddress = "",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.POSTMARK_CONFIG, config)

		val service = createService()
		val result = service.sendEmail(
			to = "recipient@example.com",
			subject = "Test Subject",
			bodyHtml = "<p>Test body</p>"
		)

		assertTrue(result is EmailResult.Failure)
		assertTrue((result as EmailResult.Failure).reason.contains("From address"))
	}
}
