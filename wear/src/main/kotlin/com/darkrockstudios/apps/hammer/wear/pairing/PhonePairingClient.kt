package com.darkrockstudios.apps.hammer.wear.pairing

import android.content.Context
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

interface PhonePairingClient {
	/** The node id of a reachable phone running Hammer, or null when there is none. */
	suspend fun findPhone(): String?

	suspend fun sendRequest(nodeId: String, request: PairRequest)
}

class DataLayerPhonePairingClient(context: Context) : PhonePairingClient {
	private val capabilityClient = Wearable.getCapabilityClient(context)
	private val messageClient = Wearable.getMessageClient(context)

	override suspend fun findPhone(): String? {
		val capability = capabilityClient
			.getCapability(PairingProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
			.await()
		val nodes = capability.nodes
		return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
	}

	override suspend fun sendRequest(nodeId: String, request: PairRequest) {
		messageClient
			.sendMessage(nodeId, PairingProtocol.REQUEST_PATH, PairingProtocol.encodeRequest(request))
			.await()
	}
}
