package com.darkrockstudios.apps.hammer.android.wear

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearPairingHandlerTest {

	private class RecordingResponder : PairingResponder {
		val sent = mutableListOf<Pair<String, PairResponse>>()

		override suspend fun respond(nodeId: String, response: PairResponse) {
			sent += nodeId to response
		}
	}

	private val phoneSettings = ServerSettings(
		url = "hammer.ink",
		email = "writer@example.com",
		userId = 7,
		bearerToken = "phone-auth",
		refreshToken = "phone-refresh",
	)
	private val watchSettings = phoneSettings.copy(bearerToken = "watch-auth", refreshToken = "watch-refresh")
	private val request = PairRequest(requestId = "req-1", installId = "watch-install", deviceLabel = "Pixel Watch")

	private val responder = RecordingResponder()
	private val pairedInstalls = mutableListOf<String>()

	private fun handler(
		settings: ServerSettings? = phoneSettings,
		pairResult: CResult<ServerSettings> = CResult.success(watchSettings),
	) = WearPairingHandler(
		serverSettings = { settings },
		pairInstall = { installId ->
			pairedInstalls += installId
			pairResult
		},
		responder = responder,
	)

	@Test
	fun `approving mints a session for the watch's install and sends it back`() = runTest {
		val response = handler().approve("watch-node", request)

		assertEquals(PairResponse.Success("req-1", watchSettings), response)
		assertEquals(listOf("watch-install"), pairedInstalls)
		assertEquals(listOf("watch-node" to response), responder.sent)
	}

	@Test
	fun `a phone that is not signed in tells the watch so without calling the server`() = runTest {
		val response = handler(settings = null).approve("watch-node", request)

		assertEquals(PairResponse.Error("req-1", PairErrorCode.NotSignedIn), response)
		assertEquals(emptyList<String>(), pairedInstalls)
		assertEquals(listOf("watch-node" to response), responder.sent)
	}

	@Test
	fun `a server rejection is reported to the watch`() = runTest {
		val response = handler(pairResult = CResult.failure(error = "nope")).approve("watch-node", request)

		assertEquals(PairResponse.Error("req-1", PairErrorCode.ServerRejected), response)
		assertEquals(listOf("watch-node" to response), responder.sent)
	}

	@Test
	fun `declining tells the watch without minting anything`() = runTest {
		val response = handler().decline("watch-node", request)

		assertEquals(PairResponse.Error("req-1", PairErrorCode.Declined), response)
		assertEquals(emptyList<String>(), pairedInstalls)
		assertEquals(listOf("watch-node" to response), responder.sent)
	}

	@Test
	fun `a phone that cannot raise the prompt tells the watch rather than going quiet`() = runTest {
		handler().reportUnavailable("watch-node", request)

		assertEquals(
			listOf("watch-node" to PairResponse.Error("req-1", PairErrorCode.PhoneUnavailable)),
			responder.sent,
		)
		assertEquals(emptyList<String>(), pairedInstalls)
	}

	@Test
	fun `a request from a newer protocol version is refused`() = runTest {
		val response = handler().approve("watch-node", request.copy(version = 99))

		assertEquals(PairResponse.Error("req-1", PairErrorCode.Unsupported), response)
		assertEquals(emptyList<String>(), pairedInstalls)
	}
}
