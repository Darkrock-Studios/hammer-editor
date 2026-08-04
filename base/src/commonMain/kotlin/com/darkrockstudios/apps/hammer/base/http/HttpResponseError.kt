package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class HttpResponseError(
	/** Message meant for logging */
	val error: String,
	/** A translated message to display to the user */
	val displayMessage: String,
	/**
	 * Stable, machine-readable reason from [ApiErrorCode]. Null when the server predates
	 * error codes, or when the failure has no code worth branching on.
	 */
	val errorCode: String? = null,
) {
	override fun toString() = displayMessage
}

/**
 * Reasons a request failed, stable across releases and independent of the translated
 * [HttpResponseError.displayMessage]. Plain strings rather than an enum so an unknown
 * value from a newer server deserializes instead of throwing.
 */
object ApiErrorCode {
	/** Wrong password, or no such account. Deliberately one code — see AccountsRepository.login. */
	const val INVALID_CREDENTIALS = "invalid_credentials"
	const val ACCOUNT_EXISTS = "account_exists"
	const val ACCOUNT_PENDING_DELETION = "account_pending_deletion"
	const val INVALID_EMAIL = "invalid_email"
	const val PASSWORD_TOO_SHORT = "password_too_short"
	const val PASSWORD_TOO_LONG = "password_too_long"
	const val PASSWORD_INVALID = "password_invalid"
	const val NOT_WHITELISTED = "not_whitelisted"
	const val TOKEN_INVALID = "token_invalid"
}