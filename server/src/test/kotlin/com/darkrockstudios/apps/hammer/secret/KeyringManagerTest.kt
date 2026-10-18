package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.utils.BaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KeyringManagerTest : BaseTest() {

	private lateinit var secureRandom: SecureRandom
	private lateinit var codec: KeyringCodec

	@BeforeEach
	override fun setup() {
		super.setup()
		secureRandom = SecureRandom()
		codec = KeyringCodec(secureRandom, Base64.Default)
	}

	private fun manager(provider: ServerSecretProvider) = KeyringManager(provider, codec)

	@Test
	fun `loads the keyring from the provider`() {
		val keyring = codec.generate()
		val provider = object : ServerSecretProvider {
			override fun loadKeyring() = codec.serialize(keyring)
		}

		assertEquals(keyring.content.activeKey(), manager(provider).activeContentKey())
	}

	@Test
	fun `no keyring yields null`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}
		assertNull(manager(provider).keyringOrNull())
	}

	@Test
	fun `requireContentKey fails fast when nothing is available`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}
		assertFailsWith<MissingKeyringException> { manager(provider).requireContentKey() }
	}

	@Test
	fun `a malformed keyring fails with a clear error, not a raw parse exception`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String = "{ this is not valid json"
		}
		assertFailsWith<MalformedKeyringException> { manager(provider).keyringOrNull() }
	}

	@Test
	fun `an invalid keyring (active id missing) fails with a clear error`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String =
				"""{"schema":1,"content":{"active":"v9","keys":{"v1":"a"}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		}
		assertFailsWith<MalformedKeyringException> { manager(provider).keyringOrNull() }
	}
}
