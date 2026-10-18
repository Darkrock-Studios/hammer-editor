package com.darkrockstudios.apps.hammer.common.components.serverreauthentication

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge

interface ServerReauthentication {
	val state: Value<State>

	data class State(
		val showReauth: Boolean = true,
		val serverWorking: Boolean = false,
		val serverUrl: String,
		val serverEmail: String,
		val serverPassword: String = "",
		val serverError: String? = null,
		/** Set once the password was accepted and the server is waiting for a second factor. */
		val mfaChallenge: MfaChallenge? = null,
	)

	fun reauthenticate(password: String)
	fun cancelReauthentication()
	fun updateServerPassword(password: String)

	/** Answers the pending [State.mfaChallenge] with a TOTP or recovery code. */
	fun submitMfaCode(code: String)
	/** Drops the pending challenge and returns to the password step. */
	fun cancelMfa()
}