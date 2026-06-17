package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.utilities.DATA_DIR
import kotlinx.serialization.SerializationException
import okio.FileSystem
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
 * Resolves the server keyring from the configured provider, grandfathering a
 * pre-existing legacy `server.secret` when no keyring document is present.
 * Read-only: never generates or writes key material. The result is cached.
 */
class KeyringManager(
	private val provider: ServerSecretProvider,
	private val codec: KeyringCodec,
	private val fileSystem: FileSystem,
	private val legacySecretPath: Path,
) {
	private val keyring: Keyring? by lazy { load() }

	private fun load(): Keyring? {
		provider.loadKeyring()?.let { json ->
			return try {
				codec.parse(json)
			} catch (e: SerializationException) {
				throw MalformedKeyringException(e)
			} catch (e: IllegalArgumentException) {
				throw MalformedKeyringException(e)
			}
		}

		// A pre-existing single secret from before keyrings: wrap it verbatim so
		// existing content still decrypts. Absence here is intentional, not an error.
		if (fileSystem.exists(legacySecretPath)) {
			val legacy = fileSystem.read(legacySecretPath) { readUtf8() }
			return codec.grandfather(legacy)
		}
		return null
	}

	fun keyringOrNull(): Keyring? = keyring

	/** The active content key, or throws if no keyring is available. */
	fun activeContentKey(): String = requireKeyring().content.activeKey()

	/** The active content key id, or throws if no keyring is available. */
	fun activeContentKeyId(): String = requireKeyring().content.active

	fun contentKey(keyId: String): String = requireKeyring().content.key(keyId)

	/** The active token-HMAC key, or null when no keyring is available. */
	fun tokenHmacKeyOrNull(): String? = keyring?.tokenHmac?.activeKey()

	/** Boot-time assertion that a content key exists; fails fast with guidance otherwise. */
	fun requireContentKey() {
		requireKeyring()
	}

	private fun requireKeyring(): Keyring =
		keyring ?: throw MissingKeyringException("The configured provider returned nothing and no legacy server.secret was found.")

	companion object {
		fun defaultKeyringPath(): Path =
			System.getProperty("user.home").toPath() / DATA_DIR / "server.keyring.json"

		fun legacySecretPath(): Path =
			System.getProperty("user.home").toPath() / DATA_DIR / "server.secret"
	}
}
