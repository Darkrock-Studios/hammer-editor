package com.darkrockstudios.apps.hammer.account;

import com.darkrockstudios.apps.hammer.appMain

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertFalse

class AccountRoutesKtTest {

	@Test
	fun `Account - Refresh Token - No User`() = testApplication {
		application {
			appMain()
		}
		client.post("/account/refresh_token/0").apply {
			assertFalse(status.isSuccess())
		}
	}
}