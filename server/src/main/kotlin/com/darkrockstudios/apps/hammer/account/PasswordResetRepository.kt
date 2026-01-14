package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.hashPassword
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import com.github.mustachejava.DefaultMustacheFactory
import java.io.StringWriter
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PasswordResetRepository(
	private val accountDao: AccountDao,
	private val passwordResetTokenDao: PasswordResetTokenDao,
	private val authTokenDao: AuthTokenDao,
	private val emailService: EmailService,
	private val clock: Clock,
	base64: Base64,
) {
	private val tokenLifetime = 15.minutes
	private val rateLimitWindow = 1.hours
	private val maxRequestsPerWindow = 3

	private val resetTokenGenerator = SecureTokenGenerator(RESET_TOKEN_LENGTH, base64)

	/**
	 * Request a password reset. Sends email if account exists.
	 * Always returns success to avoid email enumeration.
	 */
	suspend fun requestPasswordReset(email: String, resetUrl: (String) -> String): PasswordResetResult {
		// Always perform timing-safe operations to prevent enumeration
		val account = accountDao.findAccount(email)

		if (account == null) {
			// Don't reveal that account doesn't exist
			// But still perform similar operations for timing consistency
			resetTokenGenerator.generateToken()
			return PasswordResetResult.Success
		}

		// Check rate limiting
		val recentCount = passwordResetTokenDao.getRecentTokenCount(
			account.id,
			clock.now() - rateLimitWindow
		)
		if (recentCount >= maxRequestsPerWindow) {
			// Still return success to prevent enumeration, but don't send email
			return PasswordResetResult.Success
		}

		// Generate and store token
		val token = resetTokenGenerator.generateToken()
		val expires = clock.now() + tokenLifetime
		passwordResetTokenDao.createToken(account.id, token, expires)

		// Send email
		val resetLink = resetUrl(token)
		val emailResult = emailService.sendEmail(
			to = email,
			subject = "Reset Your Hammer Password",
			bodyHtml = buildResetEmailHtml(resetLink),
			bodyText = buildResetEmailText(resetLink)
		)

		// Log email failure but still return success to user
		if (emailResult is EmailResult.Failure) {
			// Log for admin debugging but don't expose to user
			println("Failed to send password reset email to $email: ${emailResult.reason}")
		}

		return PasswordResetResult.Success
	}

	/**
	 * Validate a reset token without consuming it
	 */
	suspend fun validateResetToken(token: String): TokenValidationResult {
		val resetToken = passwordResetTokenDao.getTokenByToken(token)
			?: return TokenValidationResult.Invalid

		if (resetToken.used) {
			return TokenValidationResult.AlreadyUsed
		}

		val expiresInstant = sqliteDateTimeStringToInstant(resetToken.expires)
		if (clock.now() > expiresInstant) {
			return TokenValidationResult.Expired
		}

		return TokenValidationResult.Valid(resetToken.user_id)
	}

	/**
	 * Reset password using a valid token
	 * - Updates password hash and salt
	 * - Invalidates all auth tokens (logs out all devices)
	 * - Marks reset token as used
	 */
	suspend fun resetPassword(token: String, newPassword: String): SResult<Unit> {
		val validationResult = validateResetToken(token)
		if (validationResult !is TokenValidationResult.Valid) {
			return SResult.failure(
				"Invalid or expired token",
				Msg.r("password_reset_error_invalid_token")
			)
		}

		val userId = validationResult.userId

		// Validate password
		val passwordResult = PasswordValidator.validate(newPassword)
		if (passwordResult != PasswordValidationResult.VALID) {
			return SResult.failure(
				"password failure",
				InvalidPassword.getMessage(passwordResult),
				InvalidPassword(passwordResult)
			)
		}

		val hashedPassword = hashPassword(newPassword)

		// Update password in database
		accountDao.updatePassword(userId, hashedPassword)

		// Invalidate all auth tokens (logout all devices)
		authTokenDao.deleteTokensByUserId(userId)

		// Mark reset token as used
		passwordResetTokenDao.markTokenAsUsed(token)

		return SResult.success(Unit)
	}

	/**
	 * Clean up expired tokens (call periodically or on startup)
	 */
	suspend fun cleanupExpiredTokens() {
		passwordResetTokenDao.deleteExpiredTokens()
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
		const val RESET_TOKEN_LENGTH = 32
		private val mustacheFactory = DefaultMustacheFactory("templates")
	}
}

sealed class PasswordResetResult {
	object Success : PasswordResetResult()
}

sealed class TokenValidationResult {
	data class Valid(val userId: Long) : TokenValidationResult()
	object Invalid : TokenValidationResult()
	object Expired : TokenValidationResult()
	object AlreadyUsed : TokenValidationResult()
}
