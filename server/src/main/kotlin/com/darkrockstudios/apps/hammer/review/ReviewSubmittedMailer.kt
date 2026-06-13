package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import org.slf4j.LoggerFactory
import java.util.Locale

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
		val messages = ReviewMailTemplates.loadMessages(locale)
		fun m(key: String, vararg args: Any): String =
			ReviewMailTemplates.format(messages, key, *args)

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
			bodyHtml = ReviewMailTemplates.render("email/review-submitted.mustache", model),
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
		fun t(key: String, vararg args: Any): String =
			ReviewMailTemplates.format(messages, key, *args)

		return buildList {
			add(t("review_submitted_title"))
			add("")
			add(t("review_submitted_intro", reviewerLabel, projectName, tally))
			add("")
			add("${t("review_submitted_button")}:")
			add(reviewUrl)
			add("")
			add("---")
			add(t("review_invite_footer"))
		}.joinToString("\n")
	}

	companion object {
		private val logger = LoggerFactory.getLogger(ReviewSubmittedMailer::class.java)
	}
}
