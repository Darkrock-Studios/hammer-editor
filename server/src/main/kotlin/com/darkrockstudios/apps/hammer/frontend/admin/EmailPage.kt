package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// GET /admin/email - Email Settings page
internal fun Route.adminEmailPage(
	configRepository: ConfigRepository,
	emailService: EmailService,
	patreonFeatureEnabled: Boolean
) {
	get("/email") {
		val smtpConfig = configRepository.get(AdminServerConfig.SMTP_CONFIG)
		val isConfigured = emailService.isConfigured()

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
			"smtpHost" to smtpConfig.host,
			"smtpPort" to smtpConfig.port,
			"smtpUsername" to smtpConfig.username,
			"smtpPassword" to smtpConfig.password,
			"smtpFromAddress" to smtpConfig.fromAddress,
			"smtpFromName" to smtpConfig.fromName,
			"smtpUseTls" to smtpConfig.useTls,
			"smtpUseStartTls" to smtpConfig.useStartTls,
		)
		call.respond(MustacheContent("admin-email.mustache", call.withDefaults(model)))
	}
}

internal fun Route.emailSettingsRoutes(configRepository: ConfigRepository, emailService: EmailService) {
	route("/email") {
		// POST /admin/email/settings - Save email settings
		hx.post("/settings") {
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

		// POST /admin/email/test - Send test email
		hx.post("/test") {
			val params = call.receiveParameters()
			val testRecipient = params["testRecipient"]?.trim().orEmpty()

			call.application.environment.log.info("Test email requested for recipient: $testRecipient")

			if (testRecipient.isEmpty()) {
				call.application.environment.log.warn("Test email failed: No recipient provided")
				call.respondText(
					"<div class=\"error-message\">${call.msg("admin_email_error_recipient_required")}</div>",
					ContentType.Text.Html
				)
				return@post
			}

			if (!emailService.isConfigured()) {
				call.application.environment.log.warn("Test email failed: Email service not configured")
				call.respondText(
					"<div class=\"error-message\">${call.msg("admin_email_error_not_configured")}</div>",
					ContentType.Text.Html
				)
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
					call.respondText(
						"<div class=\"success-message\">${call.msg("admin_email_test_success")}</div>",
						ContentType.Text.Html
					)
				}

				is EmailResult.Failure -> {
					call.application.environment.log.error(
						"Test email failed to $testRecipient: ${result.reason}",
						result.exception
					)
					call.respondText(
						"<div class=\"error-message\">${call.msg("admin_email_test_failed")}: ${result.reason}</div>",
						ContentType.Text.Html
					)
				}
			}
		}
	}
}