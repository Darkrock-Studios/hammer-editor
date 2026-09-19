package com.darkrockstudios.apps.hammer.wear.components.signin

import com.arkivanov.decompose.value.Value

interface ManualSignIn {
	val state: Value<State>

	fun updateServer(server: String)
	fun updateEmail(email: String)
	fun updatePassword(password: String)
	fun signIn()

	/** Called once the local network prompt is answered, whichever way. */
	fun onLocalNetworkPermissionResult()

	/** The password is deliberately absent: it never leaves the component. */
	data class State(
		val server: String = "",
		val serverInsecure: Boolean = false,
		val email: String = "",
		val hasPassword: Boolean = false,
		val busy: Boolean = false,
		val error: SignInError? = null,
		/** The server is on the local network and signing in has to wait for the prompt. */
		val localNetworkBlocked: Boolean = false,
	)

	sealed interface SignInError {
		data object MissingFields : SignInError
		data object TermsRequired : SignInError
		data object LocalNetworkDenied : SignInError
		data class Message(val text: String?) : SignInError
	}
}
