package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeychainAuthTokenStoreTest {

	private lateinit var json: Json
	private lateinit var settings: MapSettings

	private val url = "hammer.ink"
	private val userId = 1L
	private val tokens = AuthTokens(bearerToken = "zxc456", refreshToken = "bnm789")

	@BeforeTest
	fun setup() {
		json = createJsonSerializer()
		settings = MapSettings()
	}

	private fun createStore(keychain: MapSettings = settings) = KeychainAuthTokenStore(
		json = json,
		keychain = keychain,
	)

	@Test
	fun tokensRoundTripThroughTheStore() {
		val store = createStore()

		store.put(url, userId, tokens)

		assertEquals(tokens, store.get(url, userId))
	}

	@Test
	fun keyingByUrlAndUserIdIsolatesAccounts() {
		val store = createStore()
		store.put(url, userId, tokens)

		assertEquals(tokens, store.get(url, userId))
		assertNull(store.get("other.example.com", userId))
		assertNull(store.get(url, 999L))
	}

	@Test
	fun removeClearsASingleAccount() {
		val store = createStore()
		store.put(url, userId, tokens)
		store.put("other.example.com", 2L, AuthTokens("a", "b"))

		store.remove(url, userId)

		assertNull(store.get(url, userId))
		assertEquals(AuthTokens("a", "b"), store.get("other.example.com", 2L))
	}

	@Test
	fun separateKeychainsAreIsolated() {
		createStore().put(url, userId, tokens)

		val other = createStore(keychain = MapSettings())
		assertNull(other.get(url, userId))
	}

	@Test
	fun missingEntryYieldsNoTokens() {
		assertNull(createStore().get(url, userId))
	}
}
