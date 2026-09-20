package com.darkrockstudios.apps.hammer.wear.components.pairing

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState

interface Pairing {
	val state: Value<PairingState>

	fun startPairing()
	fun cancel()
}
