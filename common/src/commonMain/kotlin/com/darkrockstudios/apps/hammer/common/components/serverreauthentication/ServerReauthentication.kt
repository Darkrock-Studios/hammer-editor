package com.darkrockstudios.apps.hammer.common.components.serverreauthentication

import com.arkivanov.decompose.value.Value

interface ServerReauthentication {
	val state: Value<State>

	data class State(
		val showReauth: Boolean = true,
		val serverWorking: Boolean = false,
		val serverUrl: String,
		val serverEmail: String,
		val serverPassword: String = "",
		val serverError: String? = null,
	)

	fun reauthenticate(password: String)
	fun cancelReauthentication()
	fun updateServerPassword(password: String)
}