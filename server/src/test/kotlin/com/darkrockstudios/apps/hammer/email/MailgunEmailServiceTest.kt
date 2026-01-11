package com.darkrockstudios.apps.hammer.email

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailgunEmailServiceTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase
	private lateinit var dao: ServerConfigDao
	private lateinit var configRepository: ConfigRepository

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SqliteTestDatabase()
		db.initialize()
		dao = ServerConfigDao(db)
		configRepository = ConfigRepository(dao)

		setupKoin()
	}

	private fun createService() = MailgunEmailService(configRepository)

	@Test
	fun `isConfigured - returns false when API key is empty`() = runTest {
		val config = MailgunConfig(
			apiKey = "",
			domain = "mg.example.com",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false when domain is empty`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false when from address is empty`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "mg.example.com",
			fromAddress = "",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false when all are empty`() = runTest {
		val config = MailgunConfig(
			apiKey = "",
			domain = "",
			fromAddress = "",
			fromName = ""
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns true when API key, domain and from address are set`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "mg.example.com",
			fromAddress = "test@example.com",
			fromName = "Test Server"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertTrue(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns true even with empty from name`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "mg.example.com",
			fromAddress = "test@example.com",
			fromName = ""
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		assertTrue(service.isConfigured())
	}

	@Test
	fun `isConfigured - returns false with default config`() = runTest {
		// Default config has empty API key, domain, and from address
		val service = createService()
		assertFalse(service.isConfigured())
	}

	@Test
	fun `sendEmail - returns failure when API key not configured`() = runTest {
		val config = MailgunConfig(
			apiKey = "",
			domain = "mg.example.com",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		val result = service.sendEmail(
			to = "recipient@example.com",
			subject = "Test Subject",
			bodyHtml = "<p>Test body</p>"
		)

		assertTrue(result is EmailResult.Failure)
		assertTrue((result as EmailResult.Failure).reason.contains("API key"))
	}

	@Test
	fun `sendEmail - returns failure when domain not configured`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "",
			fromAddress = "test@example.com",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

		val service = createService()
		val result = service.sendEmail(
			to = "recipient@example.com",
			subject = "Test Subject",
			bodyHtml = "<p>Test body</p>"
		)

		assertTrue(result is EmailResult.Failure)
		assertTrue((result as EmailResult.Failure).reason.contains("domain"))
	}

	@Test
	fun `sendEmail - returns failure when from address not configured`() = runTest {
		val config = MailgunConfig(
			apiKey = "test-api-key",
			domain = "mg.example.com",
			fromAddress = "",
			fromName = "Test"
		)
		configRepository.set(AdminServerConfig.MAILGUN_CONFIG, config)

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
