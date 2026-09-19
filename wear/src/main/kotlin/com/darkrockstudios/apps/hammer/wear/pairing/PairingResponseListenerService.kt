package com.darkrockstudios.apps.hammer.wear.pairing

import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PairingResponseListenerService : WearableListenerService(), KoinComponent {
	private val phonePairing: PhonePairingUseCase by inject()

	override fun onMessageReceived(event: MessageEvent) {
		if (event.path == PairingProtocol.RESPONSE_PATH) {
			phonePairing.onResponse(event.data)
		}
	}
}
