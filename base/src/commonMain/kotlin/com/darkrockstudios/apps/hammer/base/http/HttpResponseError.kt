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
	/** Set with [ApiErrorCode.MFA_REQUIRED]. */
	val mfa: MfaChallenge? = null,
) {
	override fun toString() = displayMessage
}

/**
 * Reasons a request failed, stable across releases and independent of the translated
 * [HttpResponseError.displayMessage]. Plain strings rather than an enum so an unknown
 * value from a newer server deserializes instead of throwing.
 */
object ApiErrorCode {
	/** Wrong password, or no such account. Deliberately one code: see AccountsRepository.login. */
	const val INVALID_CREDENTIALS = "invalid_credentials"
	const val ACCOUNT_EXISTS = "account_exists"
	const val ACCOUNT_PENDING_DELETION = "account_pending_deletion"
	const val INVALID_EMAIL = "invalid_email"
	// Wire error codes, not secrets.
	const val PASSWORD_TOO_SHORT = "password_too_short" // nosemgrep: generic.secrets.gitleaks.hashicorp-tf-password.hashicorp-tf-password
	const val PASSWORD_TOO_LONG = "password_too_long" // nosemgrep: generic.secrets.gitleaks.hashicorp-tf-password.hashicorp-tf-password
	const val PASSWORD_INVALID = "password_invalid" // nosemgrep: generic.secrets.gitleaks.hashicorp-tf-password.hashicorp-tf-password
	const val NOT_WHITELISTED = "not_whitelisted"
	const val TOKEN_INVALID = "token_invalid"
	/** Create refused: this account deleted a project with that id, and tombstones stay dead. */
	const val PROJECT_DELETED = "project_deleted"
	/** Create refused: another account already owns a project with that id. */
	const val PROJECT_ID_TAKEN = "project_id_taken"
	/** Upsert refused: a sibling group already has that name. Also on delete, when a child would collide. */
	const val GROUP_NAME_TAKEN = "group_name_taken"
	/** Upsert refused: the new parent is the group itself or one of its descendants. */
	const val GROUP_CYCLE = "group_cycle"
	/** Upsert refused: the result would nest deeper than MAX_GROUP_NESTING_DEPTH. */
	const val GROUP_TOO_DEEP = "group_too_deep"
	/** Upsert refused: another account already owns a group with that id. */
	const val GROUP_ID_TAKEN = "group_id_taken"
	/** Password accepted; answer [HttpResponseError.mfa] to finish signing in. */
	const val MFA_REQUIRED = "mfa_required"
	/** Wrong, reused, or expired second-factor code, or an unknown challenge. */
	const val MFA_CODE_INVALID = "mfa_code_invalid"
}