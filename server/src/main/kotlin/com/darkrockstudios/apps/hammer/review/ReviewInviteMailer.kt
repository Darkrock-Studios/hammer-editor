package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.github.mustachejava.DefaultMustacheFactory
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

class ReviewInviteMailer(
	private val emailService: EmailService,
) {
	suspend fun sendInvite(
		toEmail: String,
		authorName: String,
		projectName: String,
		note: String?,
		reviewUrl: String,
		expiresFormatted: String?,
		locale: Locale,
	): EmailResult {
		val messages = loadMessages(locale)
		fun m(key: String, vararg args: Any): String {
			val raw = messages[key] ?: key
			return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
		}

		val expiryLine = if (expiresFormatted != null) {
			m("review_invite_expires", expiresFormatted)
		} else {
			m("review_invite_no_expiry")
		}

		val model = mapOf(
			"reviewUrl" to reviewUrl,
			"authorName" to authorName,
			"projectName" to projectName,
			"note" to note,
			"hasNote" to (note.isNullOrBlank().not()),
			"expiryLine" to expiryLine,
			"msg" to messages,
			"title" to m("review_invite_title"),
			"intro" to m("review_invite_intro", authorName, projectName),
		)

		val result = emailService.sendEmail(
			to = toEmail,
			subject = m("review_invite_subject", authorName, projectName),
			bodyHtml = renderTemplate("email/review-invite.mustache", model),
			bodyText = buildTextBody(messages, authorName, projectName, note, reviewUrl, expiryLine),
		)
		if (result is EmailResult.Failure) {
			logger.error("Failed to send review invite to $toEmail: ${result.reason}")
		}
		return result
	}

	private fun buildTextBody(
		messages: Map<String, String>,
		authorName: String,
		projectName: String,
		note: String?,
		reviewUrl: String,
		expiryLine: String,
	): String {
		fun t(key: String, vararg args: Any): String {
			val raw = messages[key] ?: key
			return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
		}
		val noteBlock = if (note.isNullOrBlank()) "" else "\n${t("review_page_note_title")}:\n$note\n"
		return """
			${t("review_invite_title")}

			${t("review_invite_intro", authorName, projectName)}
			$noteBlock
			${t("review_invite_explain")}

			${t("review_invite_button")}:
			$reviewUrl

			$expiryLine

			${t("review_invite_ignore")}

			---
			${t("review_invite_footer")}
		""".trimIndent()
	}

	private fun loadMessages(locale: Locale): Map<String, String> {
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
		private val logger = LoggerFactory.getLogger(ReviewInviteMailer::class.java)
	}
}
