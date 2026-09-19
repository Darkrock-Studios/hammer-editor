package com.darkrockstudios.apps.hammer.wear.components.pairing

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import com.darkrockstudios.apps.hammer.wear.pairing.inFlight
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingComponent(
	componentContext: ComponentContext,
	private val phonePairing: PhonePairingUseCase,
	private val deviceLabel: String,
	private val onCancel: () -> Unit,
) : ComponentBase(componentContext), Pairing {

	private val _state = MutableValue(phonePairing.state.value)
	override val state: Value<PairingState> = _state

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			phonePairing.state.collect { pairingState ->
				withContext(dispatcherMain) { _state.value = pairingState }
			}
		}
		// The state outlives this screen, so only a request still in flight is worth resuming. Any
		// finished state is stale: a Paired left over from before a sign out would otherwise strand
		// the screen on "Paired" with no way to try again.
		if (!phonePairing.state.value.inFlight) {
			startPairing()
		}
	}

	override fun startPairing() {
		scope.launch { phonePairing.startPairing(deviceLabel) }
	}

	override fun cancel() {
		phonePairing.reset()
		onCancel()
	}
}
