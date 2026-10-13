package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.github.mustachejava.DefaultMustacheFactory
import java.io.StringWriter
import java.util.*

/**
 * Starts a password reset: creates the token and emails the reset link. Always returns
 * [PasswordResetResult.Success], whether or not the account exists, is rate limited, or the
 * email could be sent, so the response can't be used to find out which emails have accounts.
 */
class RequestPasswordResetUseCase(
	private val passwordResetRepository: PasswordResetRepository,
	private val emailService: EmailService,
) {
	suspend operator fun invoke(email: String, resetUrl: (String) -> String?): PasswordResetResult {
		val token = passwordResetRepository.createResetToken(email)
			?: return PasswordResetResult.Success

		// Without a trusted (config-derived) base URL we cannot build a link whose
		// host isn't attacker-influenced; skip the email rather than email a
		// poisoned link.
		val resetLink = resetUrl(token) ?: return PasswordResetResult.Success
		val emailResult = emailService.sendEmail(
			to = email,
			subject = "Reset Your Hammer Password",
			bodyHtml = buildResetEmailHtml(resetLink),
			bodyText = buildResetEmailText(resetLink)
		)

		if (emailResult is EmailResult.Failure) {
			// Log for admin debugging but don't expose to user
			println("Failed to send password reset email to $email: ${emailResult.reason}")
		}

		return PasswordResetResult.Success
	}

	private fun buildResetEmailHtml(resetLink: String): String {
		val messages = loadMessages()
		val model = mapOf(
			"resetLink" to resetLink,
			"msg" to messages
		)
		return renderTemplate("email/password-reset.mustache", model)
	}

	private fun buildResetEmailText(resetLink: String): String {
		val messages = loadMessages()
		return """
			${messages["password_reset_email_title"]}

			${messages["password_reset_email_intro"]}

			${messages["password_reset_email_cta"]}
			$resetLink

			${messages["password_reset_email_expires"]}

			${messages["password_reset_email_ignore"]}

			---
			${messages["password_reset_email_footer"]}
		""".trimIndent()
	}

	private fun loadMessages(locale: Locale = Locale.ENGLISH): Map<String, String> {
		val bundle = ResourceBundle.getBundle("i18n.Messages", locale)
		return bundle.keys.asSequence().associateWith { key -> bundle.getString(key) }
	}

	private fun renderTemplate(templatePath: String, model: Map<String, Any?>): String {
		val mustache = mustacheFactory.compile(templatePath)
		val writer = StringWriter()
		mustache.execute(writer, model)
		return writer.toString()
	}

	companion object {
		private val mustacheFactory = DefaultMustacheFactory("templates")
	}
}

sealed class PasswordResetResult {
	object Success : PasswordResetResult()
}
