package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.hashPassword
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PasswordResetRepository(
	private val accountDao: AccountDao,
	private val passwordResetTokenDao: PasswordResetTokenDao,
	private val authTokenDao: AuthTokenDao,
	private val clock: Clock,
	base64: Base64,
) {
	private val tokenLifetime = 15.minutes
	private val rateLimitWindow = 1.hours
	private val maxRequestsPerWindow = 3

	private val resetTokenGenerator = SecureTokenGenerator(RESET_TOKEN_LENGTH, base64)

	/**
	 * Creates and stores a reset token for the account with this email. Null when there is
	 * no such account or it is past the rate limit; callers must not let that difference
	 * reach the requester, see [RequestPasswordResetUseCase].
	 */
	suspend fun createResetToken(email: String): String? {
		val account = accountDao.findAccount(email)

		if (account == null) {
			// Still generate a token so an unknown email takes about as long as a known one
			resetTokenGenerator.generateToken()
			return null
		}

		val recentCount = passwordResetTokenDao.getRecentTokenCount(
			account.id,
			clock.now() - rateLimitWindow
		)
		if (recentCount >= maxRequestsPerWindow) return null

		val token = resetTokenGenerator.generateToken()
		val expires = clock.now() + tokenLifetime
		passwordResetTokenDao.createToken(account.id, token, expires)
		return token
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

		if (clock.now() > resetToken.expires) {
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

		val passwordResult = setPassword(validationResult.userId, newPassword)
		if (passwordResult != PasswordValidationResult.VALID) {
			return SResult.failure(
				"password failure",
				InvalidPassword.getMessage(passwordResult),
				InvalidPassword(passwordResult)
			)
		}

		passwordResetTokenDao.markTokenAsUsed(token)

		return SResult.success(Unit)
	}

	/**
	 * Validates and stores [newPassword] for [userId], then signs out every device.
	 * Nothing is written unless the result is [PasswordValidationResult.VALID].
	 */
	suspend fun setPassword(userId: Long, newPassword: String): PasswordValidationResult {
		val passwordResult = PasswordValidator.validate(newPassword)
		if (passwordResult != PasswordValidationResult.VALID) return passwordResult

		accountDao.updatePassword(userId, hashPassword(newPassword))
		authTokenDao.deleteTokensByUserId(userId)

		return PasswordValidationResult.VALID
	}

	/**
	 * Command-line recovery for a locked-out admin. Only admin accounts qualify;
	 * [email] is required once the server has more than one.
	 */
	suspend fun resetAdminPassword(email: String?, newPassword: String): AdminPasswordResetResult {
		val target = if (email == null) {
			val admins = accountDao.getAdmins()
			when (admins.size) {
				0 -> return AdminPasswordResetResult.NoAdmin
				1 -> admins.single()
				else -> return AdminPasswordResetResult.MultipleAdmins(admins.map { it.email })
			}
		} else {
			val account = accountDao.findAccount(email) ?: return AdminPasswordResetResult.NoSuchAccount(email)
			if (!account.is_admin) return AdminPasswordResetResult.NotAdmin(email)
			account
		}

		return when (val passwordResult = setPassword(target.id, newPassword)) {
			PasswordValidationResult.VALID -> AdminPasswordResetResult.Success(target.email)
			else -> AdminPasswordResetResult.PasswordRejected(passwordResult)
		}
	}

	/**
	 * Clean up expired tokens (call periodically or on startup)
	 */
	suspend fun cleanupExpiredTokens() {
		passwordResetTokenDao.deleteExpiredTokens()
	}

	companion object {
		const val RESET_TOKEN_LENGTH = 32
	}
}

sealed class AdminPasswordResetResult {
	data class Success(val email: String) : AdminPasswordResetResult()
	object NoAdmin : AdminPasswordResetResult()
	data class MultipleAdmins(val emails: List<String>) : AdminPasswordResetResult()
	data class NoSuchAccount(val email: String) : AdminPasswordResetResult()
	data class NotAdmin(val email: String) : AdminPasswordResetResult()
	data class PasswordRejected(val result: PasswordValidationResult) : AdminPasswordResetResult()
}

sealed class TokenValidationResult {
	data class Valid(val userId: Long) : TokenValidationResult()
	object Invalid : TokenValidationResult()
	object Expired : TokenValidationResult()
	object AlreadyUsed : TokenValidationResult()
}
