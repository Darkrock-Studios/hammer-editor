package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.utilities.DATA_DIR
import kotlinx.serialization.SerializationException
import okio.Path
import okio.Path.Companion.toPath

class MissingKeyringException(detail: String) : IllegalStateException(
	"No server keyring is available, but encryption is enabled. $detail Generate one with the " +
		"'generate-keyring' subcommand and make it available to the configured secret provider."
)

class MalformedKeyringException(cause: Throwable) : IllegalStateException(
	"The configured server keyring could not be parsed: ${cause.message}. Check the secret " +
		"provider's contents, or regenerate it with 'generate-keyring'.",
	cause,
)

/**
 * Resolves the server keyring from the configured provider. Read-only: never
 * generates or writes key material. The result is cached.
 */
class KeyringManager(
	private val provider: ServerSecretProvider,
	private val codec: KeyringCodec,
) {
	private val keyring: Keyring? by lazy { load() }

	private fun load(): Keyring? {
		val json = provider.loadKeyring() ?: return null
		return try {
			codec.parse(json)
		} catch (e: SerializationException) {
			throw MalformedKeyringException(e)
		} catch (e: IllegalArgumentException) {
			throw MalformedKeyringException(e)
		}
	}

	fun keyringOrNull(): Keyring? = keyring

	/** The active content key, or throws if no keyring is available. */
	fun activeContentKey(): String = requireKeyring().content.activeKey()

	/** The active content key id, or throws if no keyring is available. */
	fun activeContentKeyId(): String = requireKeyring().content.active

	fun contentKey(keyId: String): String = requireKeyring().content.key(keyId)

	/** The active token-HMAC key, or null when no keyring is available. */
	fun tokenHmacKeyOrNull(): String? = keyring?.tokenHmac?.activeKey()

	/** Every token-HMAC generation still in the keyring, or null when no keyring is available. */
	fun tokenHmacKeysOrNull(): List<String>? = keyring?.tokenHmac?.keys?.values?.toList()

	/** Boot-time assertion that a content key exists; fails fast with guidance otherwise. */
	fun requireContentKey() {
		requireKeyring()
	}

	private fun requireKeyring(): Keyring =
		keyring ?: throw MissingKeyringException(
			"The configured provider returned no keyring. Generate one with 'generate-keyring', or " +
				"if this server still has a pre-keyring server.secret, convert it with 'migrate-secret'."
		)

	companion object {
		fun defaultKeyringPath(): Path =
			System.getProperty("user.home").toPath() / DATA_DIR / "server.keyring.json"

		/** The pre-keyring `server.secret`; only `migrate-secret` and the upgrade guard read it. */
		fun legacySecretPath(): Path =
			System.getProperty("user.home").toPath() / DATA_DIR / "server.secret"
	}
}
