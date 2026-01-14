package com.darkrockstudios.apps.hammer.base.validate

/**
 * Shared email validator used by both client and server.
 * This ensures consistent email validation across the application.
 */
object EmailValidator {
	// Simple email pattern that checks for basic format: something@something
	// The local part can't contain @, and domain must have at least one character
	// More comprehensive RFC 5322 regex is available but often overkill for user validation
	private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[^@]+$")

	/**
	 * Validates an email address format.
	 *
	 * @param email The email address to validate
	 * @return true if the email format is valid, false otherwise
	 */
	fun validate(email: String): Boolean {
		val trimmedInput = email.trim()
		return trimmedInput.isNotBlank() && emailPattern.matches(trimmedInput)
	}
}
