package com.darkrockstudios.apps.hammer.wear.pairing

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhonePairingUseCaseTest {

	private class FakePhoneClient : PhonePairingClient {
		var phoneNodeId: String? = "phone-node"
		var lookupFailure: Exception? = null
		var sendFailure: Exception? = null
		val sent = mutableListOf<Pair<String, PairRequest>>()

		override suspend fun findPhone(): String? {
			lookupFailure?.let { throw it }
			return phoneNodeId
		}

		override suspend fun sendRequest(nodeId: String, request: PairRequest) {
			sendFailure?.let { throw it }
			sent += nodeId to request
		}
	}

	private val watchSettings = ServerSettings(
		url = "hammer.ink",
		email = "writer@example.com",
		userId = 7,
		bearerToken = "watch-auth",
		refreshToken = "watch-refresh",
	)

	private lateinit var client: FakePhoneClient
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var accountUseCase: AccountUseCase
	private lateinit var useCase: PhonePairingUseCase

	@BeforeEach
	fun setUp() {
		client = FakePhoneClient()
		globalSettingsStore = mockk()
		coEvery { globalSettingsStore.ensureInstallId() } returns "watch-install"
		accountUseCase = mockk()
		every { accountUseCase.applyPairedSettings(any()) } returns CResult.success()
		useCase = PhonePairingUseCase(client, globalSettingsStore, accountUseCase)
	}

	private fun pendingRequestId(): String = client.sent.single().second.requestId

	@Test
	fun `no reachable phone reports it and sends nothing`() = runTest {
		client.phoneNodeId = null

		useCase.startPairing("Pixel Watch")

		assertEquals(PairingState.PhoneNotFound, useCase.state.value)
		assertEquals(emptyList<Pair<String, PairRequest>>(), client.sent)
	}

	@Test
	fun `an unavailable wearable api counts as no phone`() = runTest {
		client.lookupFailure = IllegalStateException("Wearable API unavailable")

		useCase.startPairing("Pixel Watch")

		assertEquals(PairingState.PhoneNotFound, useCase.state.value)
	}

	@Test
	fun `a request for this install goes to the phone and waits for confirmation`() = runTest {
		useCase.startPairing("Pixel Watch")

		val (nodeId, request) = client.sent.single()
		assertEquals("phone-node", nodeId)
		assertEquals("watch-install", request.installId)
		assertEquals("Pixel Watch", request.deviceLabel)
		assertEquals(PairingState.AwaitingConfirmation, useCase.state.value)
	}

	@Test
	fun `a failed send is reported as a local failure`() = runTest {
		client.sendFailure = IllegalStateException("out of range")

		useCase.startPairing("Pixel Watch")

		assertEquals(PairingState.Failed(reason = null), useCase.state.value)
	}

	@Test
	fun `the phone's session is adopted when it answers the pending request`() = runTest {
		useCase.startPairing("Pixel Watch")

		useCase.onResponse(PairingProtocol.encodeResponse(PairResponse.Success(pendingRequestId(), watchSettings)))

		assertEquals(PairingState.Paired, useCase.state.value)
		verify(exactly = 1) { accountUseCase.applyPairedSettings(watchSettings) }
	}

	@Test
	fun `an answer to some other request is ignored`() = runTest {
		useCase.startPairing("Pixel Watch")

		useCase.onResponse(PairingProtocol.encodeResponse(PairResponse.Success("stale-request", watchSettings)))

		assertEquals(PairingState.AwaitingConfirmation, useCase.state.value)
		verify(exactly = 0) { accountUseCase.applyPairedSettings(any()) }
	}

	@Test
	fun `a declined request surfaces the reason`() = runTest {
		useCase.startPairing("Pixel Watch")

		useCase.onResponse(
			PairingProtocol.encodeResponse(PairResponse.Error(pendingRequestId(), PairErrorCode.Declined))
		)

		assertEquals(PairingState.Failed(PairErrorCode.Declined), useCase.state.value)
	}

	@Test
	fun `a reply the phone could not address is applied to the pending request`() = runTest {
		useCase.startPairing("Pixel Watch")

		useCase.onResponse(
			PairingProtocol.encodeResponse(PairResponse.Error(requestId = "", code = PairErrorCode.Unsupported))
		)

		assertEquals(PairingState.Failed(PairErrorCode.Unsupported), useCase.state.value)
	}

	@Test
	fun `a reply with no request pending is ignored even when it is unaddressed`() = runTest {
		useCase.onResponse(
			PairingProtocol.encodeResponse(PairResponse.Error(requestId = "", code = PairErrorCode.Unsupported))
		)

		assertEquals(PairingState.Idle, useCase.state.value)
	}

	@Test
	fun `a reply that lands after the screen is gone is still adopted`() = runTest {
		useCase.startPairing("Pixel Watch")
		val requestId = pendingRequestId()

		// Leaving the pairing screen no longer abandons the request, so this still applies.
		useCase.onResponse(PairingProtocol.encodeResponse(PairResponse.Success(requestId, watchSettings)))

		assertEquals(PairingState.Paired, useCase.state.value)
		verify(exactly = 1) { accountUseCase.applyPairedSettings(watchSettings) }
	}

	@Test
	fun `a reply after reset is ignored`() = runTest {
		useCase.startPairing("Pixel Watch")
		val requestId = pendingRequestId()
		useCase.reset()

		useCase.onResponse(PairingProtocol.encodeResponse(PairResponse.Success(requestId, watchSettings)))

		assertEquals(PairingState.Idle, useCase.state.value)
		verify(exactly = 0) { accountUseCase.applyPairedSettings(any()) }
	}
}
