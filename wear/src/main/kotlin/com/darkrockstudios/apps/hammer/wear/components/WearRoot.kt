package com.darkrockstudios.apps.hammer.wear.components

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.wear.components.pairing.Pairing
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects
import com.darkrockstudios.apps.hammer.wear.components.signin.ManualSignIn
import kotlinx.serialization.Serializable

interface WearRoot {
	val stack: Value<ChildStack<Config, Destination>>

	fun showPairing()
	fun showManualSignIn()
	fun onBack()

	@Serializable
	sealed interface Config {
		@Serializable
		data object Onboarding : Config

		@Serializable
		data object Pairing : Config

		@Serializable
		data object ManualSignIn : Config

		@Serializable
		data object Projects : Config
	}

	sealed interface Destination {
		data object Onboarding : Destination
		data class PairingDestination(val component: Pairing) : Destination
		data class ManualSignInDestination(val component: ManualSignIn) : Destination
		data class ProjectsDestination(val component: WearProjects) : Destination
	}
}
