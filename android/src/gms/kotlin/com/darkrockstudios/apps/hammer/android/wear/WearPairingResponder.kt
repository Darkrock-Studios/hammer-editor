package com.darkrockstudios.apps.hammer.android.wear

import android.content.Context
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

interface PairingResponder {
	suspend fun respond(nodeId: String, response: PairResponse)
}

class WearPairingResponder(context: Context) : PairingResponder {
	private val messageClient = Wearable.getMessageClient(context)

	override suspend fun respond(nodeId: String, response: PairResponse) {
		messageClient
			.sendMessage(nodeId, PairingProtocol.RESPONSE_PATH, PairingProtocol.encodeResponse(response))
			.await()
	}
}
