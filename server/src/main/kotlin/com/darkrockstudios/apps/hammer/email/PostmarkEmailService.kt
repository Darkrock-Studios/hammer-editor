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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

class PostmarkEmailService(
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
		val config = configRepository.get(AdminServerConfig.POSTMARK_CONFIG)
		return config.serverToken.isNotBlank() && config.fromAddress.isNotBlank()
	}

	override suspend fun sendEmail(
		to: String,
		subject: String,
		bodyHtml: String,
		bodyText: String?
	): EmailResult = withContext(ioDispatcher) {
		try {
			println("PostmarkEmailService: Starting to send email to: $to")
			val config = configRepository.get(AdminServerConfig.POSTMARK_CONFIG)

			if (config.serverToken.isBlank()) {
				println("PostmarkEmailService: Server token not configured")
				return@withContext EmailResult.Failure("Postmark server token not configured")
			}
			if (config.fromAddress.isBlank()) {
				println("PostmarkEmailService: From address not configured")
				return@withContext EmailResult.Failure("From address not configured")
			}

			val textToUse = bodyText ?: stripHtml(bodyHtml)

			val fromField = if (config.fromName.isNotBlank()) {
				"${config.fromName} <${config.fromAddress}>"
			} else {
				config.fromAddress
			}

			val request = PostmarkEmailRequest(
				from = fromField,
				to = to,
				subject = subject,
				htmlBody = bodyHtml,
				textBody = textToUse
			)

			println("PostmarkEmailService: Sending request to Postmark API...")
			val response = httpClient.post(POSTMARK_API_URL) {
				header("X-Postmark-Server-Token", config.serverToken)
				header(HttpHeaders.Accept, ContentType.Application.Json)
				header(HttpHeaders.ContentType, ContentType.Application.Json)
				setBody(request)
			}

			when {
				response.status.isSuccess() -> {
					println("PostmarkEmailService: Email sent successfully!")
					EmailResult.Success
				}

				else -> {
					val errorBody = response.bodyAsText()
					println("PostmarkEmailService: API error ${response.status}: $errorBody")
					EmailResult.Failure("Postmark API error: ${response.status} - $errorBody")
				}
			}
			// Send boundary: any failure becomes an EmailResult.Failure.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			println("PostmarkEmailService: ERROR sending email: ${e.message}")
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
		private const val POSTMARK_API_URL = "https://api.postmarkapp.com/email"
	}
}

@Serializable
private data class PostmarkEmailRequest(
	@SerialName("From")
	val from: String,
	@SerialName("To")
	val to: String,
	@SerialName("Subject")
	val subject: String,
	@SerialName("HtmlBody")
	val htmlBody: String,
	@SerialName("TextBody")
	val textBody: String
)
