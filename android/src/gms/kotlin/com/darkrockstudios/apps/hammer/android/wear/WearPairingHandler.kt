package com.darkrockstudios.apps.hammer.android.wear

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Answers a watch's pairing request once the user has approved or declined it on the phone. */
class WearPairingHandler(
	private val serverSettings: () -> ServerSettings?,
	private val pairInstall: suspend (installId: String) -> CResult<ServerSettings>,
	private val responder: PairingResponder,
) {
	fun isSignedIn(): Boolean {
		val settings = serverSettings() ?: return false
		return settings.userId >= 0 && !settings.bearerToken.isNullOrBlank()
	}

	suspend fun approve(nodeId: String, request: PairRequest): PairResponse {
		val response = when {
			request.version != PairingProtocol.VERSION ->
				PairResponse.Error(request.requestId, PairErrorCode.Unsupported)

			!isSignedIn() ->
				PairResponse.Error(request.requestId, PairErrorCode.NotSignedIn)

			else -> {
				val result = pairInstall(request.installId)
				if (isSuccess(result)) {
					PairResponse.Success(request.requestId, result.data)
				} else {
					PairResponse.Error(request.requestId, PairErrorCode.ServerRejected)
				}
			}
		}
		send(nodeId, response)
		return response
	}

	suspend fun decline(nodeId: String, request: PairRequest): PairResponse {
		val response = PairResponse.Error(request.requestId, PairErrorCode.Declined)
		send(nodeId, response)
		return response
	}

	/** Replies to a request this phone could not read, so the watch stops waiting. */
	suspend fun rejectUnreadable(nodeId: String) {
		send(nodeId, PairResponse.Error(requestId = "", code = PairErrorCode.Unsupported))
	}

	private suspend fun send(nodeId: String, response: PairResponse) {
		try {
			responder.respond(nodeId, response)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			// The watch may have gone out of range; it can retry from its own screen.
			Napier.w("Failed to reply to the watch", e)
		}
	}
}
