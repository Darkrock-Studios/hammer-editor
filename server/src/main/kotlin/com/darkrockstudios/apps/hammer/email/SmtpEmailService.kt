package com.darkrockstudios.apps.hammer.email

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.util.*

class SmtpEmailService(
	private val configRepository: ConfigRepository,
) : EmailService, KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	override suspend fun isConfigured(): Boolean {
		val config = configRepository.get(AdminServerConfig.SMTP_CONFIG)
		return config.host.isNotBlank() && config.fromAddress.isNotBlank()
	}

	override suspend fun sendEmail(
		to: String,
		subject: String,
		bodyHtml: String,
		bodyText: String?
	): EmailResult = withContext(ioDispatcher) {
		try {
			println("SmtpEmailService: Starting to send email to: $to")
			val config = configRepository.get(AdminServerConfig.SMTP_CONFIG)

			println("SmtpEmailService: SMTP Config - host=${config.host}, port=${config.port}, from=${config.fromAddress}, username=${config.username}, useTls=${config.useTls}, useStartTls=${config.useStartTls}")

			if (config.host.isBlank()) {
				println("SmtpEmailService: SMTP host not configured")
				return@withContext EmailResult.Failure("SMTP host not configured")
			}
			if (config.fromAddress.isBlank()) {
				println("SmtpEmailService: From address not configured")
				return@withContext EmailResult.Failure("From address not configured")
			}

			val props = Properties().apply {
				put("mail.smtp.host", config.host)
				put("mail.smtp.port", config.port.toString())

				if (config.username.isNotBlank()) {
					put("mail.smtp.auth", "true")
				}

				// Port 587 typically uses STARTTLS (not SSL)
				// Port 465 typically uses SSL/TLS
				if (config.useStartTls) {
					put("mail.smtp.starttls.enable", "true")
					put("mail.smtp.starttls.required", "true")
					// Don't use SSL when using STARTTLS
					put("mail.smtp.ssl.enable", "false")
				} else if (config.useTls) {
					// Direct SSL/TLS connection (port 465)
					put("mail.smtp.ssl.enable", "true")
					put("mail.smtp.ssl.protocols", "TLSv1.2")
				}

				// Additional properties for better compatibility
				put("mail.smtp.connectiontimeout", "10000")
				put("mail.smtp.timeout", "10000")
			}

			println("SmtpEmailService: Creating mail session with auth=${config.username.isNotBlank()}")

			val session = if (config.username.isNotBlank()) {
				Session.getInstance(props, object : Authenticator() {
					override fun getPasswordAuthentication(): PasswordAuthentication {
						return PasswordAuthentication(config.username, config.password)
					}
				})
			} else {
				Session.getInstance(props)
			}

			println("SmtpEmailService: Building message")

			val message = MimeMessage(session).apply {
				setFrom(InternetAddress(config.fromAddress, config.fromName))
				setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
				setSubject(subject)

				// Create multipart message with both HTML and plain text
				val multipart = MimeMultipart("alternative")

				// Add plain text part (if provided)
				val textToUse = bodyText ?: stripHtml(bodyHtml)
				val textPart = MimeBodyPart().apply {
					setText(textToUse, "utf-8")
				}
				multipart.addBodyPart(textPart)

				// Add HTML part
				val htmlPart = MimeBodyPart().apply {
					setContent(bodyHtml, "text/html; charset=utf-8")
				}
				multipart.addBodyPart(htmlPart)

				setContent(multipart)
			}

			println("SmtpEmailService: Sending message via Transport...")
			Transport.send(message)
			println("SmtpEmailService: Email sent successfully!")
			EmailResult.Success
		} catch (e: Exception) {
			println("SmtpEmailService: ERROR sending email: ${e.message}")
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
}
