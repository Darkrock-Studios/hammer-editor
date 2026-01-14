package com.darkrockstudios.apps.hammer.base.validate

import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator.MAX_LENGTH
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator.MIN_LENGTH


/**
 * Password validation results. Used by both client and server for consistent validation.
 */
enum class PasswordValidationResult {
	VALID,
	TOO_SHORT,
	TOO_LONG,
	NO_UPPERCASE,
	NO_LOWERCASE,
	NO_NUMBER,
	NO_SPECIAL
}

/**
 * Shared password validator used by both client and server.
 * This ensures consistent password requirements across the application.
 */
object PasswordValidator {
	const val MIN_LENGTH = 8
	const val MAX_LENGTH = 64

	/**
	 * Validates a password and returns the first validation failure found,
	 * or [PasswordValidationResult.VALID] if all requirements are met.
	 *
	 * Current requirements:
	 * - Length between [MIN_LENGTH] and [MAX_LENGTH] characters
	 *
	 * Note: Additional requirements (uppercase, lowercase, number, special char)
	 * can be enabled by uncommenting the relevant checks below.
	 */
	fun validate(password: String): PasswordValidationResult {
		val trimmedInput = password.trim()
		return when {
			trimmedInput.length < MIN_LENGTH -> PasswordValidationResult.TOO_SHORT
			trimmedInput.length > MAX_LENGTH -> PasswordValidationResult.TOO_LONG
			// Uncomment to enable additional password requirements:
			// !trimmedInput.any { it.isUpperCase() } -> PasswordValidationResult.NO_UPPERCASE
			// !trimmedInput.any { it.isLowerCase() } -> PasswordValidationResult.NO_LOWERCASE
			// !trimmedInput.any { it.isDigit() } -> PasswordValidationResult.NO_NUMBER
			// !trimmedInput.any { !it.isLetterOrDigit() } -> PasswordValidationResult.NO_SPECIAL
			else -> PasswordValidationResult.VALID
		}
	}
}
