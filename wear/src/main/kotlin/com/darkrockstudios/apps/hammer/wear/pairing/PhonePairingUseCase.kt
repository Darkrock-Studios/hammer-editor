package com.darkrockstudios.apps.hammer.wear.pairing

import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed interface PairingState {
	data object Idle : PairingState
	data object SearchingPhone : PairingState
	data object AwaitingConfirmation : PairingState
	data object Paired : PairingState
	data object PhoneNotFound : PairingState

	/** [reason] is null when the failure happened on this device rather than on the phone. */
	data class Failed(val reason: PairErrorCode?) : PairingState
}

/**
 * Asks the paired phone for a session of this watch's own. The phone answers asynchronously
 * through [PairingResponseListenerService], which may outlive the screen that started the request,
 * so the state lives here rather than in a component.
 */
class PhonePairingUseCase(
	private val client: PhonePairingClient,
	private val globalSettingsStore: GlobalSettingsStore,
	private val accountUseCase: AccountUseCase,
) {
	private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
	val state: StateFlow<PairingState> = _state

	@Volatile
	private var pendingRequestId: String? = null

	suspend fun startPairing(deviceLabel: String) {
		_state.value = PairingState.SearchingPhone

		val nodeId = try {
			client.findPhone()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			// Play services throws ApiException when the Wearable API is unavailable.
			Napier.w("Phone lookup failed", e)
			null
		}
		if (nodeId == null) {
			_state.value = PairingState.PhoneNotFound
			return
		}

		val requestId = UUID.randomUUID().toString()
		pendingRequestId = requestId
		// Set before sending so a fast reply cannot be overwritten by this state.
		_state.value = PairingState.AwaitingConfirmation

		try {
			val request = PairRequest(
				requestId = requestId,
				installId = globalSettingsStore.ensureInstallId(),
				deviceLabel = deviceLabel,
			)
			client.sendRequest(nodeId, request)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to send the pairing request", e)
			pendingRequestId = null
			_state.value = PairingState.Failed(reason = null)
		}
	}

	fun onResponse(payload: ByteArray) {
		val response = PairingProtocol.decodeResponse(payload)
		if (response == null) {
			Napier.w("Ignoring an unreadable pairing response")
			return
		}
		if (response.requestId != pendingRequestId) {
			Napier.w("Ignoring a pairing response for a request that is no longer pending")
			return
		}
		pendingRequestId = null

		_state.value = when (response) {
			is PairResponse.Success -> {
				if (accountUseCase.applyPairedSettings(response.settings).isSuccess) {
					PairingState.Paired
				} else {
					PairingState.Failed(reason = null)
				}
			}

			is PairResponse.Error -> PairingState.Failed(reason = response.code)
		}
	}

	fun reset() {
		pendingRequestId = null
		_state.value = PairingState.Idle
	}
}
