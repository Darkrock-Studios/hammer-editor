package com.darkrockstudios.apps.hammer.common.components.protocolmismatch

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProtocolMismatch : HammerComponent {
	val state: Value<State>

	fun openReleaseUrl()
	fun dismiss()

	data class State(
		val clientProtocolVersion: Int,
		val serverProtocolVersion: Int?,
		val clientIsBehind: Boolean,
		val currentVersion: String,
		val latestVersionTag: String? = null,
		val isNewVersionAvailable: Boolean = false,
		val releaseUrl: String? = null,
	)
}
