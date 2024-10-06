package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.utilities.RandomString
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertEquals

class TokenTest {
	@Test
	fun `Token Generator`() {
		val b64 = createTokenBase64()
		val tokenGenerator = SecureTokenGenerator(Token.LENGTH, b64)
		val token = tokenGenerator.generateToken()
		assertEquals(Token.LENGTH, b64.decode(token).size)
	}

	@Test
	fun `Random String Generator`() = runTest {
		val length = 5
		val generator = RandomString(length, SecureRandom())
		val randomString = generator.nextString()
		assertEquals(length, randomString.length)
	}
}