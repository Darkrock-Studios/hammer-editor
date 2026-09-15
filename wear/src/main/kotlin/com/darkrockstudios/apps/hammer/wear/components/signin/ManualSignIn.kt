package com.darkrockstudios.apps.hammer.wear.components.signin

import com.arkivanov.decompose.value.Value

interface ManualSignIn {
	val state: Value<State>

	fun updateServer(server: String)
	fun updateEmail(email: String)
	fun updatePassword(password: String)
	fun signIn()

	/** The password is deliberately absent: it never leaves the component. */
	data class State(
		val server: String = "",
		val email: String = "",
		val hasPassword: Boolean = false,
		val busy: Boolean = false,
		val error: SignInError? = null,
	)

	sealed interface SignInError {
		data object MissingFields : SignInError
		data object TermsRequired : SignInError
		data class Message(val text: String?) : SignInError
	}
}
