package com.darkrockstudios.apps.hammer.base.http

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpResponseErrorTest {
	private val json = createNetworkJsonSerializer()

	@Test
	fun oldShapeWithoutMfaDecodes() {
		val old = """{"error":"Bad","displayMessage":"Nope","errorCode":"invalid_credentials"}"""

		val decoded = json.decodeFromString(HttpResponseError.serializer(), old)

		assertEquals(HttpResponseError("Bad", "Nope", ApiErrorCode.INVALID_CREDENTIALS), decoded)
	}

	@Test
	fun shapeWithoutMfaRoundTrips() {
		val error = HttpResponseError("Bad", "Nope", ApiErrorCode.INVALID_CREDENTIALS)

		val decoded = json.decodeFromString(HttpResponseError.serializer(), json.encodeToString(HttpResponseError.serializer(), error))

		assertEquals(error, decoded)
	}

	@Test
	fun mfaChallengeRoundTrips() {
		val error = HttpResponseError(
			error = "MFA required",
			displayMessage = "Enter your code",
			errorCode = ApiErrorCode.MFA_REQUIRED,
			mfa = MfaChallenge("tok", listOf(MfaMethod.TOTP, MfaMethod.RECOVERY)),
		)

		val decoded = json.decodeFromString(HttpResponseError.serializer(), json.encodeToString(HttpResponseError.serializer(), error))

		assertEquals(error, decoded)
	}

	@Test
	fun unknownMethodFromNewerServerDecodes() {
		val newer = """{"error":"e","displayMessage":"d","errorCode":"mfa_required",""" +
			""""mfa":{"token":"t","methods":["totp","passkey"],"extra":1}}"""

		val decoded = json.decodeFromString(HttpResponseError.serializer(), newer)

		assertEquals(listOf("totp", "passkey"), decoded.mfa?.methods)
	}
}
