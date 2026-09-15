package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.value.Value

interface WearProjects {
	val state: Value<State>

	fun signOut()

	data class State(
		val accountEmail: String? = null,
	)
}
