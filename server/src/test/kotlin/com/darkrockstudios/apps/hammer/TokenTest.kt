package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.utilities.RandomString
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenTest {
	@Test
	fun `Token Generator`() {
		val tokenGenerator = SecureTokenGenerator(Token.LENGTH)
		val token = tokenGenerator.generateToken()
		assertEquals(Token.LENGTH, token.length)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Random String Generator`() = runTest {
		val length = 5
		val generator = RandomString(length)
		val randomString = generator.nextString()
		assertEquals(length, randomString.length)
	}
}