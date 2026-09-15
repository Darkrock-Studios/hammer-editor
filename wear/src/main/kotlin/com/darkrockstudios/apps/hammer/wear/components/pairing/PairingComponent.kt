package com.darkrockstudios.apps.hammer.wear.components.pairing

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
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
		if (phonePairing.state.value == PairingState.Idle) {
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
