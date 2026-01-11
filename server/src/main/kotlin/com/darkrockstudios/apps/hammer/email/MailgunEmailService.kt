package com.darkrockstudios.apps.hammer.email

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.util.*

class MailgunEmailService(
	private val configRepository: ConfigRepository,
) : EmailService, KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	private val httpClient = HttpClient(Java)

	override suspend fun isConfigured(): Boolean {
		val config = configRepository.get(AdminServerConfig.MAILGUN_CONFIG)
		return config.apiKey.isNotBlank() &&
			config.domain.isNotBlank() &&
			config.fromAddress.isNotBlank()
	}

	override suspend fun sendEmail(
		to: String,
		subject: String,
		bodyHtml: String,
		bodyText: String?
	): EmailResult = withContext(ioDispatcher) {
		try {
			println("MailgunEmailService: Starting to send email to: $to")
			val config = configRepository.get(AdminServerConfig.MAILGUN_CONFIG)

			if (config.apiKey.isBlank()) {
				println("MailgunEmailService: API key not configured")
				return@withContext EmailResult.Failure("Mailgun API key not configured")
			}
			if (config.domain.isBlank()) {
				println("MailgunEmailService: Domain not configured")
				return@withContext EmailResult.Failure("Mailgun domain not configured")
			}
			if (config.fromAddress.isBlank()) {
				println("MailgunEmailService: From address not configured")
				return@withContext EmailResult.Failure("From address not configured")
			}

			val textToUse = bodyText ?: stripHtml(bodyHtml)

			val fromField = if (config.fromName.isNotBlank()) {
				"${config.fromName} <${config.fromAddress}>"
			} else {
				config.fromAddress
			}

			val baseUrl = if (config.useEuRegion) {
				MAILGUN_EU_API_URL
			} else {
				MAILGUN_US_API_URL
			}
			val apiUrl = "$baseUrl/${config.domain}/messages"

			// Basic auth credentials: username "api", password = API key
			val credentials = Base64.getEncoder().encodeToString("api:${config.apiKey}".toByteArray())

			println("MailgunEmailService: Sending request to Mailgun API...")
			val response = httpClient.post(apiUrl) {
				header(HttpHeaders.Authorization, "Basic $credentials")
				setBody(FormDataContent(Parameters.build {
					append("from", fromField)
					append("to", to)
					append("subject", subject)
					append("text", textToUse)
					append("html", bodyHtml)
				}))
			}

			when {
				response.status.isSuccess() -> {
					println("MailgunEmailService: Email sent successfully!")
					EmailResult.Success
				}

				else -> {
					val errorBody = response.bodyAsText()
					println("MailgunEmailService: API error ${response.status}: $errorBody")
					EmailResult.Failure("Mailgun API error: ${response.status} - $errorBody")
				}
			}
		} catch (e: Exception) {
			println("MailgunEmailService: ERROR sending email: ${e.message}")
			e.printStackTrace()
			EmailResult.Failure(e.message ?: "Unknown error sending email", e)
		}
	}

	private fun stripHtml(html: String): String {
		return html
			.replace(Regex("<br\\s*/?>"), "\n")
			.replace(Regex("<p\\s*>"), "\n")
			.replace(Regex("</p>"), "\n")
			.replace(Regex("<[^>]*>"), "")
			.replace(Regex("&nbsp;"), " ")
			.replace(Regex("&amp;"), "&")
			.replace(Regex("&lt;"), "<")
			.replace(Regex("&gt;"), ">")
			.replace(Regex("&quot;"), "\"")
			.trim()
	}

	companion object {
		private const val MAILGUN_US_API_URL = "https://api.mailgun.net/v3"
		private const val MAILGUN_EU_API_URL = "https://api.eu.mailgun.net/v3"
	}
}
