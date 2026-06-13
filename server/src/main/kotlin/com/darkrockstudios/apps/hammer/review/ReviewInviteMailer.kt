package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import org.slf4j.LoggerFactory
import java.util.Locale

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
		val messages = ReviewMailTemplates.loadMessages(locale)
		fun m(key: String, vararg args: Any): String =
			ReviewMailTemplates.format(messages, key, *args)

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
			bodyHtml = ReviewMailTemplates.render("email/review-invite.mustache", model),
			bodyText = buildTextBody(messages, authorName, projectName, note, reviewUrl, expiryLine),
		)
		if (result is EmailResult.Failure) {
			logger.error("Failed to send review invite to $toEmail: ${result.reason}")
		}
		return result
	}

	// Built line-by-line: trimIndent on an interpolated block breaks as soon as
	// a multi-line note lands at column zero.
	private fun buildTextBody(
		messages: Map<String, String>,
		authorName: String,
		projectName: String,
		note: String?,
		reviewUrl: String,
		expiryLine: String,
	): String {
		fun t(key: String, vararg args: Any): String =
			ReviewMailTemplates.format(messages, key, *args)

		return buildList {
			add(t("review_invite_title"))
			add("")
			add(t("review_invite_intro", authorName, projectName))
			if (!note.isNullOrBlank()) {
				add("")
				add("${t("review_page_note_title")}:")
				add(note)
			}
			add("")
			add(t("review_invite_explain"))
			add("")
			add("${t("review_invite_button")}:")
			add(reviewUrl)
			add("")
			add(expiryLine)
			add("")
			add(t("review_invite_ignore"))
			add("")
			add("---")
			add(t("review_invite_footer"))
		}.joinToString("\n")
	}

	companion object {
		private val logger = LoggerFactory.getLogger(ReviewInviteMailer::class.java)
	}
}
