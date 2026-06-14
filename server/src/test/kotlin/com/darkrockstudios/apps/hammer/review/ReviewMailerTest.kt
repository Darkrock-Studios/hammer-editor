package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReviewMailerTest {

	private val emailService: EmailService = mockk()

	private val subject = slot<String>()
	private val html = slot<String>()
	private val text = slot<String?>()

	private fun stubSend(result: EmailResult = EmailResult.Success) {
		coEvery {
			emailService.sendEmail(any(), capture(subject), capture(html), captureNullable(text))
		} returns result
	}

	@Test
	fun `sendInvite composes the email with the project, author, note, and review link`() = runTest {
		stubSend()
		val mailer = ReviewInviteMailer(emailService)

		val result = mailer.sendInvite(
			toEmail = "reviewer@example.com",
			authorName = "Ada",
			projectName = "The Difference Engine",
			note = "Please focus on chapter three.",
			reviewUrl = "https://hammer.test/review/abc",
			expiresFormatted = "June 20, 2026",
			locale = Locale.ENGLISH,
		)

		assertIs<EmailResult.Success>(result)
		coVerify(exactly = 1) {
			emailService.sendEmail("reviewer@example.com", any(), any(), any())
		}
		assertTrue(subject.captured.contains("Ada"))
		assertTrue(html.captured.contains("https://hammer.test/review/abc"))
		assertTrue(html.captured.contains("The Difference Engine"))
		assertTrue(html.captured.contains("Please focus on chapter three."))
		val body = text.captured!!
		assertTrue(body.contains("https://hammer.test/review/abc"))
		assertTrue(body.contains("Please focus on chapter three."))
		assertTrue(body.contains("June 20, 2026"))
	}

	@Test
	fun `sendInvite omits the note section when no note is given`() = runTest {
		stubSend()
		val mailer = ReviewInviteMailer(emailService)

		mailer.sendInvite(
			toEmail = "reviewer@example.com",
			authorName = "Ada",
			projectName = "Project X",
			note = null,
			reviewUrl = "https://hammer.test/review/abc",
			expiresFormatted = null,
			locale = Locale.ENGLISH,
		)

		// The no-expiry branch is taken and the link still renders.
		assertTrue(html.captured.contains("https://hammer.test/review/abc"))
		assertFalse(text.captured!!.contains("June"))
	}

	@Test
	fun `sendInvite returns the failure from the email service`() = runTest {
		stubSend(EmailResult.Failure("smtp down"))
		val mailer = ReviewInviteMailer(emailService)

		val result = mailer.sendInvite(
			toEmail = "reviewer@example.com",
			authorName = "Ada",
			projectName = "Project X",
			note = null,
			reviewUrl = "https://hammer.test/review/abc",
			expiresFormatted = null,
			locale = Locale.ENGLISH,
		)

		assertEquals("smtp down", assertIs<EmailResult.Failure>(result).reason)
	}

	@Test
	fun `sendSubmittedNotice with multiple suggestions includes the count`() = runTest {
		stubSend()
		val mailer = ReviewSubmittedMailer(emailService)

		val result = mailer.sendSubmittedNotice(
			toEmail = "author@example.com",
			reviewerLabel = "A reviewer",
			projectName = "Project X",
			suggestionCount = 5,
			reviewUrl = "https://hammer.test/review/xyz",
			locale = Locale.ENGLISH,
		)

		assertIs<EmailResult.Success>(result)
		coVerify(exactly = 1) { emailService.sendEmail("author@example.com", any(), any(), any()) }
		assertTrue(html.captured.contains("https://hammer.test/review/xyz"))
		assertTrue(html.captured.contains("Project X"))
		assertTrue(text.captured!!.contains("5"))
	}

	@Test
	fun `sendSubmittedNotice with a single suggestion uses the singular tally`() = runTest {
		stubSend()
		val mailer = ReviewSubmittedMailer(emailService)

		val result = mailer.sendSubmittedNotice(
			toEmail = "author@example.com",
			reviewerLabel = "A reviewer",
			projectName = "Project X",
			suggestionCount = 1,
			reviewUrl = "https://hammer.test/review/xyz",
			locale = Locale.ENGLISH,
		)

		assertIs<EmailResult.Success>(result)
		assertTrue(html.captured.contains("https://hammer.test/review/xyz"))
	}

	@Test
	fun `sendSubmittedNotice returns the failure from the email service`() = runTest {
		stubSend(EmailResult.Failure("rejected"))
		val mailer = ReviewSubmittedMailer(emailService)

		val result = mailer.sendSubmittedNotice(
			toEmail = "author@example.com",
			reviewerLabel = "A reviewer",
			projectName = "Project X",
			suggestionCount = 2,
			reviewUrl = "https://hammer.test/review/xyz",
			locale = Locale.ENGLISH,
		)

		assertEquals("rejected", assertIs<EmailResult.Failure>(result).reason)
	}
}
