package com.darkrockstudios.apps.hammer.e2e

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains

class TeapotTest : E2eTest() {
	@Test
	fun test(): Unit = runBlocking {
		val response: String = HttpClient().get("http://127.0.0.1:8080/api/teapot").body()
		assertContains(response, "I'm a little Tea Pot")
	}
}