package data.pairing

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingProtocolTest {

	@Test
	fun `a request survives the round trip`() {
		val request = PairRequest(requestId = "req-1", installId = "watch-install", deviceLabel = "Pixel Watch")

		val decoded = PairingProtocol.decodeRequest(PairingProtocol.encodeRequest(request))

		assertEquals(request, decoded)
	}

	@Test
	fun `a success response carries the settings through the round trip`() {
		val response = PairResponse.Success(
			requestId = "req-1",
			settings = ServerSettings(
				url = "hammer.ink",
				email = "writer@example.com",
				userId = 7,
				bearerToken = "watch-auth",
				refreshToken = "watch-refresh",
			),
		)

		val decoded = PairingProtocol.decodeResponse(PairingProtocol.encodeResponse(response))

		assertEquals(response, decoded)
	}

	@Test
	fun `an error response survives the round trip`() {
		val response = PairResponse.Error(requestId = "req-1", code = PairErrorCode.Declined)

		val decoded = PairingProtocol.decodeResponse(PairingProtocol.encodeResponse(response))

		assertEquals(response, decoded)
	}

	@Test
	fun `the protocol version travels with the request`() {
		val request = PairRequest(requestId = "req-1", installId = "watch-install", deviceLabel = "Pixel Watch")

		val encoded = PairingProtocol.encodeRequest(request).decodeToString()

		// Left off the wire, a future version decodes as this one and the compatibility check is dead.
		assertTrue(encoded.contains("\"version\""))
		assertEquals(99, PairingProtocol.decodeRequest(PairingProtocol.encodeRequest(request.copy(version = 99)))?.version)
	}

	@Test
	fun `a malformed payload decodes to null rather than throwing`() {
		val garbage = "not json".encodeToByteArray()

		assertNull(PairingProtocol.decodeRequest(garbage))
		assertNull(PairingProtocol.decodeResponse(garbage))
	}
}
