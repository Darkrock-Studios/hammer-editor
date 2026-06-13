package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.github.mustachejava.DefaultMustacheFactory
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** Tells the author their reviewer has submitted, with a link to the review page. */
class ReviewSubmittedMailer(
	private val emailService: EmailService,
) {
	suspend fun sendSubmittedNotice(
		toEmail: String,
		reviewerLabel: String,
		projectName: String,
		suggestionCount: Long,
		reviewUrl: String,
		locale: Locale,
	): EmailResult {
		val messages = loadMessages(locale)
		fun m(key: String, vararg args: Any): String {
			val raw = messages[key] ?: key
			return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
		}

		val tally = if (suggestionCount == 1L) {
			m("review_submit_tally_one")
		} else {
			m("review_submit_tally_many", suggestionCount)
		}

		val model = mapOf(
			"reviewUrl" to reviewUrl,
			"msg" to messages,
			"title" to m("review_submitted_title"),
			"intro" to m("review_submitted_intro", reviewerLabel, projectName, tally),
		)

		val result = emailService.sendEmail(
			to = toEmail,
			subject = m("review_submitted_subject", reviewerLabel, projectName),
			bodyHtml = renderTemplate("email/review-submitted.mustache", model),
			bodyText = buildTextBody(messages, reviewerLabel, projectName, tally, reviewUrl),
		)
		if (result is EmailResult.Failure) {
			logger.error("Failed to send review-submitted notice to $toEmail: ${result.reason}")
		}
		return result
	}

	private fun buildTextBody(
		messages: Map<String, String>,
		reviewerLabel: String,
		projectName: String,
		tally: String,
		reviewUrl: String,
	): String {
		fun t(key: String, vararg args: Any): String {
			val raw = messages[key] ?: key
			return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
		}
		return """
			${t("review_submitted_title")}

			${t("review_submitted_intro", reviewerLabel, projectName, tally)}

			${t("review_submitted_button")}:
			$reviewUrl

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
		private val logger = LoggerFactory.getLogger(ReviewSubmittedMailer::class.java)
	}
}
