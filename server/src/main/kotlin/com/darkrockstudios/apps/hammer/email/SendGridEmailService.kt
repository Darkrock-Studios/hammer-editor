package com.darkrockstudios.apps.hammer.email

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

class SendGridEmailService(
	private val configRepository: ConfigRepository,
) : EmailService, KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	private val httpClient = HttpClient(Java) {
		install(ContentNegotiation) {
			json(json)
		}
	}

	override suspend fun isConfigured(): Boolean {
		val config = configRepository.get(AdminServerConfig.SENDGRID_CONFIG)
		return config.apiKey.isNotBlank() && config.fromAddress.isNotBlank()
	}

	override suspend fun sendEmail(
		to: String,
		subject: String,
		bodyHtml: String,
		bodyText: String?
	): EmailResult = withContext(ioDispatcher) {
		try {
			println("SendGridEmailService: Starting to send email to: $to")
			val config = configRepository.get(AdminServerConfig.SENDGRID_CONFIG)

			if (config.apiKey.isBlank()) {
				println("SendGridEmailService: API key not configured")
				return@withContext EmailResult.Failure("SendGrid API key not configured")
			}
			if (config.fromAddress.isBlank()) {
				println("SendGridEmailService: From address not configured")
				return@withContext EmailResult.Failure("From address not configured")
			}

			val textToUse = bodyText ?: stripHtml(bodyHtml)

			val request = SendGridMailRequest(
				personalizations = listOf(
					SendGridPersonalization(
						to = listOf(SendGridEmailAddress(email = to))
					)
				),
				from = SendGridEmailAddress(
					email = config.fromAddress,
					name = config.fromName.ifBlank { null }
				),
				subject = subject,
				content = listOf(
					SendGridContent(type = "text/plain", value = textToUse),
					SendGridContent(type = "text/html", value = bodyHtml)
				)
			)

			println("SendGridEmailService: Sending request to SendGrid API...")
			val response = httpClient.post(SENDGRID_API_URL) {
				header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
				header(HttpHeaders.ContentType, ContentType.Application.Json)
				setBody(request)
			}

			when {
				response.status == HttpStatusCode.Accepted -> {
					println("SendGridEmailService: Email sent successfully!")
					EmailResult.Success
				}

				response.status.isSuccess() -> {
					println("SendGridEmailService: Email sent successfully with status ${response.status}")
					EmailResult.Success
				}

				else -> {
					val errorBody = response.bodyAsText()
					println("SendGridEmailService: API error ${response.status}: $errorBody")
					EmailResult.Failure("SendGrid API error: ${response.status} - $errorBody")
				}
			}
			// Send boundary: any failure becomes an EmailResult.Failure.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			println("SendGridEmailService: ERROR sending email: ${e.message}")
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
		private const val SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send"
	}
}

@Serializable
private data class SendGridMailRequest(
	val personalizations: List<SendGridPersonalization>,
	val from: SendGridEmailAddress,
	val subject: String,
	val content: List<SendGridContent>
)

@Serializable
private data class SendGridPersonalization(
	val to: List<SendGridEmailAddress>
)

@Serializable
private data class SendGridEmailAddress(
	val email: String,
	val name: String? = null
)

@Serializable
private data class SendGridContent(
	val type: String,
	val value: String
)
