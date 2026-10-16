package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

/** Issued when a correct password needs a second factor; answered at `/api/account/login/mfa`. */
@Serializable
data class MfaChallenge(
	val token: String,
	/** Values from [MfaMethod] the account accepts for this challenge. */
	val methods: List<String>,
)

/** Plain strings so a method added by a newer server deserializes on an older client. */
object MfaMethod {
	const val TOTP = "totp"
	const val RECOVERY = "recovery"
}
