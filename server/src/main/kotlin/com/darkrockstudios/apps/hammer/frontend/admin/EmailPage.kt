package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.email.EmailProvider
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.respondHtmlFragment
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.div

// GET /admin/email - Email Settings page
internal fun Route.adminEmailPage(
	configRepository: ConfigRepository,
	emailService: EmailService,
	patreonFeatureEnabled: Boolean,
	serverConfig: ServerConfig
) {
	get("/email") {
		val smtpConfig = configRepository.get(AdminServerConfig.SMTP_CONFIG)
		val sendGridConfig = configRepository.get(AdminServerConfig.SENDGRID_CONFIG)
		val postmarkConfig = configRepository.get(AdminServerConfig.POSTMARK_CONFIG)
		val mailgunConfig = configRepository.get(AdminServerConfig.MAILGUN_CONFIG)
		val isConfigured = emailService.isConfigured()
		val activeProvider = serverConfig.emailProviderType ?: EmailProvider.SMTP

		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to false,
			"activePatreon" to false,
			"activeEmail" to true,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"emailFeatureEnabled" to true,
			"emailConfigured" to isConfigured,
			"activeProvider" to activeProvider.name,
			"isSmtpProvider" to (activeProvider == EmailProvider.SMTP),
			"isSendGridProvider" to (activeProvider == EmailProvider.SENDGRID),
			"isPostmarkProvider" to (activeProvider == EmailProvider.POSTMARK),
			"isMailgunProvider" to (activeProvider == EmailProvider.MAILGUN),
			// SMTP config
			"smtpHost" to smtpConfig.host,
			"smtpPort" to smtpConfig.port,
			"smtpUsername" to smtpConfig.username,
			"smtpPassword" to smtpConfig.password,
			"smtpFromAddress" to smtpConfig.fromAddress,
			"smtpFromName" to smtpConfig.fromName,
			"smtpUseTls" to smtpConfig.useTls,
			"smtpUseStartTls" to smtpConfig.useStartTls,
			// SendGrid config
			"sendGridApiKey" to sendGridConfig.apiKey,
			"sendGridFromAddress" to sendGridConfig.fromAddress,
			"sendGridFromName" to sendGridConfig.fromName,
			// Postmark config
			"postmarkServerToken" to postmarkConfig.serverToken,
			"postmarkFromAddress" to postmarkConfig.fromAddress,
			"postmarkFromName" to postmarkConfig.fromName,
			// Mailgun config
			"mailgunApiKey" to mailgunConfig.apiKey,
			"mailgunDomain" to mailgunConfig.domain,
			"mailgunFromAddress" to mailgunConfig.fromAddress,
			"mailgunFromName" to mailgunConfig.fromName,
			"mailgunUseEuRegion" to mailgunConfig.useEuRegion,
		)
		call.respond(MustacheContent("admin-email.mustache", call.withDefaults(model)))
	}
}

internal fun Route.emailSettingsRoutes(configRepository: ConfigRepository, emailService: EmailService) {
	route("/email") {
		// POST /admin/email/settings/smtp - Save SMTP settings
		hx.post("/settings/smtp") {
			val params = call.receiveParameters()
			val host = params["host"]?.trim().orEmpty()
			val portStr = params["port"]?.trim().orEmpty()
			val username = params["username"]?.trim().orEmpty()
			val password = params["password"]?.trim().orEmpty()
			val fromAddress = params["fromAddress"]?.trim().orEmpty()
			val fromName = params["fromName"]?.trim().orEmpty()
			val useTls = params["useTls"] == "true"
			val useStartTls = params["useStartTls"] == "true"

			val currentConfig = configRepository.get(AdminServerConfig.SMTP_CONFIG)
			val port = portStr.toIntOrNull() ?: 587

			val newConfig = currentConfig.copy(
				host = host,
				port = port,
				username = username,
				password = password.ifEmpty { currentConfig.password },
				fromAddress = fromAddress,
				fromName = fromName.ifEmpty { call.msg("admin_email_default_from_name") },
				useTls = useTls,
				useStartTls = useStartTls,
			)

			configRepository.set(AdminServerConfig.SMTP_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		// POST /admin/email/settings/sendgrid - Save SendGrid settings
		hx.post("/settings/sendgrid") {
			val params = call.receiveParameters()
			val apiKey = params["apiKey"]?.trim().orEmpty()
			val fromAddress = params["fromAddress"]?.trim().orEmpty()
			val fromName = params["fromName"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.SENDGRID_CONFIG)

			val newConfig = currentConfig.copy(
				apiKey = apiKey.ifEmpty { currentConfig.apiKey },
				fromAddress = fromAddress,
				fromName = fromName.ifEmpty { call.msg("admin_email_default_from_name") },
			)

			configRepository.set(AdminServerConfig.SENDGRID_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		// POST /admin/email/settings/postmark - Save Postmark settings
		hx.post("/settings/postmark") {
			val params = call.receiveParameters()
			val serverToken = params["serverToken"]?.trim().orEmpty()
			val fromAddress = params["fromAddress"]?.trim().orEmpty()
			val fromName = params["fromName"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.POSTMARK_CONFIG)

			val newConfig = currentConfig.copy(
				serverToken = serverToken.ifEmpty { currentConfig.serverToken },
				fromAddress = fromAddress,
				fromName = fromName.ifEmpty { call.msg("admin_email_default_from_name") },
			)

			configRepository.set(AdminServerConfig.POSTMARK_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		// POST /admin/email/settings/mailgun - Save Mailgun settings
		hx.post("/settings/mailgun") {
			val params = call.receiveParameters()
			val apiKey = params["apiKey"]?.trim().orEmpty()
			val domain = params["domain"]?.trim().orEmpty()
			val fromAddress = params["fromAddress"]?.trim().orEmpty()
			val fromName = params["fromName"]?.trim().orEmpty()
			val useEuRegion = params["useEuRegion"] == "true"

			val currentConfig = configRepository.get(AdminServerConfig.MAILGUN_CONFIG)

			val newConfig = currentConfig.copy(
				apiKey = apiKey.ifEmpty { currentConfig.apiKey },
				domain = domain,
				fromAddress = fromAddress,
				fromName = fromName.ifEmpty { call.msg("admin_email_default_from_name") },
				useEuRegion = useEuRegion,
			)

			configRepository.set(AdminServerConfig.MAILGUN_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		// POST /admin/email/test - Send test email
		hx.post("/test") {
			val params = call.receiveParameters()
			val testRecipient = params["testRecipient"]?.trim().orEmpty()

			call.application.environment.log.info("Test email requested for recipient: $testRecipient")

			if (testRecipient.isEmpty()) {
				call.application.environment.log.warn("Test email failed: No recipient provided")
				val errorMsg = call.msg("admin_email_error_recipient_required")
				call.respondHtmlFragment {
					div("error-message") {
						+errorMsg
					}
				}
				return@post
			}

			if (!emailService.isConfigured()) {
				call.application.environment.log.warn("Test email failed: Email service not configured")
				val errorMsg = call.msg("admin_email_error_not_configured")
				call.respondHtmlFragment {
					div("error-message") {
						+errorMsg
					}
				}
				return@post
			}

			call.application.environment.log.info("Attempting to send test email to: $testRecipient")

			val result = emailService.sendEmail(
				to = testRecipient,
				subject = call.msg("admin_email_test_email_subject"),
				bodyHtml = call.msg("admin_email_test_email_html"),
				bodyText = call.msg("admin_email_test_email_text")
			)

			when (result) {
				is EmailResult.Success -> {
					call.application.environment.log.info("Test email sent successfully to: $testRecipient")
					val successMsg = call.msg("admin_email_test_success")
					call.respondHtmlFragment {
						div("success-message") {
							+successMsg
						}
					}
				}

				is EmailResult.Failure -> {
					call.application.environment.log.error(
						"Test email failed to $testRecipient: ${result.reason}",
						result.exception
					)
					val errorMsg = "${call.msg("admin_email_test_failed")}: ${result.reason}"
					call.respondHtmlFragment {
						div("error-message") {
							+errorMsg
						}
					}
				}
			}
		}
	}
}